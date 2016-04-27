package org.kost.android.droidmeter;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.DashPathEffect;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.androidplot.util.PixelUtils;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.CatmullRomInterpolator;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.io.Serializable;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SerialConsoleActivity extends AppCompatActivity {

    UsbDevice device;
    UsbManager usbManager;

    int readcount;
    byte[] readbuf=new byte[16];

    private static final String ACTION_USB_PERMISSION = "org.kost.android.droidmeter";

    private final String TAG = SerialConsoleActivity.class.getSimpleName();

    private XYPlot plot;
    private static final int HISTORY_SIZE = 30;

    /**
     * Driver instance, passed in statically via
     * {@link #show(Context, UsbSerialPort)}.
     *
     * <p/>
     * This is a devious hack; it'd be cleaner to re-create the driver using
     * arguments passed in with the {@link #startActivity(Intent)} intent. We
     * can get away with it because both activities will run in the same
     * process, and this is a simple demo.
     */
    private static UsbSerialPort sPort = null;

    private HandleChange changed=null;
    private int calibCounter=0;
    private int calibMax=30;
    private boolean chDetect=false;


    private TextView mTitleTextView;
    private TextView mDumpTextView;
    private ScrollView mScrollView;

    // XYSeries series1;
    SimpleXYSeries series1;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    SerialConsoleActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            SerialConsoleActivity.this.updateReceivedData(data);
                        }
                    });
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_serial_console);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.consoleText);
        mScrollView = (ScrollView) findViewById(R.id.demoScroller);

        // Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        // setSupportActionBar(toolbar);

        plot = (XYPlot) findViewById(R.id.plot);

        plot.setRangeBoundaries(1,1, BoundaryMode.AUTO);
        // create a couple arrays of y-values to plot:
        // Number[] series1Numbers = {1, 4, 2, 8, 4, 16, 8, 32, 16, 64};

        // turn the above arrays into XYSeries':
        // (Y_VALS_ONLY means use the element index as the x value)
        // series1 = new SimpleXYSeries(Arrays.asList(series1Numbers), SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "Nesto1");

        series1 = new SimpleXYSeries("Values");
        series1.useImplicitXVals();

        // create formatters to use for drawing a series using LineAndPointRenderer
        // and configure them from xml:
        LineAndPointFormatter series1Format = new LineAndPointFormatter();
        series1Format.setPointLabelFormatter(new PointLabelFormatter());
        series1Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_labels);

        LineAndPointFormatter series2Format = new LineAndPointFormatter();
        series2Format.setPointLabelFormatter(new PointLabelFormatter());
        series2Format.configure(getApplicationContext(),
                R.xml.line_point_formatter_with_labels_2);

        // see: http://androidplot.com/smooth-curves-and-androidplot/
        series1Format.setInterpolationParams(
                new CatmullRomInterpolator.Params(10, CatmullRomInterpolator.Type.Centripetal));

        // add a new series' to the xyplot:
        plot.addSeries(series1, series1Format);

        // reduce the number of range labels
        plot.setTicksPerRangeLabel(3);

        // rotate domain labels 45 degrees to make them more compact horizontally:
        plot.getGraphWidget().setDomainLabelOrientation(-45);

        if (savedInstanceState != null) {
            savedInstanceState.getSerializable("sport");
            savedInstanceState.getBoolean("chDetect");
        }

    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putSerializable("sport", (Serializable) sPort);
        savedInstanceState.putBoolean("chDetect", chDetect);
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }
        changed.clear();
        finish();
    }

    void showStatus(TextView theTextView, String theLabel, boolean theValue){
        String msg = theLabel + ": " + (theValue ? "enabled" : "disabled") + "\n";
        theTextView.append(msg);
    }

    // Receive permission
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                            communicate();
                        }
                    }
                    else {
                        Log.d("KostSerial", "permission denied for device " + device);
                    }
                }
            }
        }
    };

    private void communicate () {
        UsbDeviceConnection connection = usbManager.openDevice(device);
        try {
            sPort.open(connection);
            sPort.setParameters(2400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            sPort.setRTS(false);
            sPort.setDTR(true);

        } catch (IOException e) {
            Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
            mTitleTextView.setText("Error opening device: " + e.getMessage());
            try {
                sPort.close();
            } catch (IOException e2) {
                // Ignore.
            }
            sPort = null;
            return;
        }
        mTitleTextView.setText("Serial device: " + sPort.getClass().getSimpleName());

        onDeviceStateChange();
    }

    @Override
    protected void onResume() {
        super.onResume();
        changed=new HandleChange(3);
        calibCounter=0;
        Log.d(TAG, "Resumed, port=" + sPort);
        if (sPort == null) {
            mTitleTextView.setText("No serial device.");
        } else {
            usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
            if (availableDrivers.isEmpty()) {
                mTitleTextView.append("No drivers available\n");
                Log.d("SerialKost", "no drivers available");
                return;
            }

            UsbSerialDriver driver = availableDrivers.get(0);
            sPort = driver.getPorts().get(0);
            device=driver.getDevice();
            UsbDeviceConnection connection = usbManager.openDevice(device);
            if (connection == null) {
                mTitleTextView.setText("Opening device failed");

                PendingIntent mPermissionIntent;

                Log.i("SerialKost", "Setting PermissionIntent -> MainMenu");
                mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
                Log.i("SerialKost", "Setting IntentFilter -> MainMenu");
                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                Log.i("SerialKost", "Setting registerReceiver -> MainMenu");
                registerReceiver(mUsbReceiver, filter);
                Log.i("SerialKost", "Setting requestPermission -> MainMenu");
                usbManager.requestPermission(device, mPermissionIntent);
                return;
            }
            communicate();
        }
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            try {
                sPort.setDTR(false);
                sPort.setRTS(false);
            } catch (IOException e) {
                // ignore e.printStackTrace();
            }
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void parseReceivedData(byte[] data) {
        ut61pkt u;
        try {
            u = new ut61pkt(data);
        } catch (NumberFormatException e) {
            Log.i(TAG,"Error reading value");
            return;
        }
        Log.i(TAG, Arrays.toString(data));
        Log.i(TAG, u.toStrDebug());

        String calibStatus = "C";
        Float dvalue = Float.valueOf(u.value);
        changed.add(dvalue);
        String minmax=String.valueOf(changed.Min)+" "+String.valueOf(changed.Max);
        if (chDetect) {
            calibStatus = "A ";
            if (changed.isChange()) {
                ToneGenerator toneG = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
                toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
            }
        }

        mTitleTextView.setText(String.valueOf(u.value) +
                " " + u.mtype + u.munit + " " +u.acdc+" "+minmax+" "+calibStatus);

        if (series1.size() > HISTORY_SIZE) {
            series1.removeFirst();
        }
        series1.addLast(null, u.value);
        plot.redraw();

        // mDumpTextView.append(u.toStr()+"\n");
        // mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());

    }

    private void updateReceivedData(byte[] data) {
        final String message = "Read " + data.length + " bytes: \n" + HexDump.dumpHexString(data) + "\n\n";
        byte[] valBuf = new byte[16];
        int numBytesRead = data.length;
        // readcount = readcount + numBytesRead;
        int potcount=readcount + numBytesRead;
        if (potcount<14) {
            System.arraycopy(data, 0, readbuf, readcount, numBytesRead);
            readcount=readcount+numBytesRead;
        }
        if (potcount>14) {
            System.arraycopy(data, 0, readbuf, readcount, 14-readcount);
            valBuf=readbuf;
            System.arraycopy(data, 14-readcount, readbuf, 0, numBytesRead-(14-readcount));
            readcount=numBytesRead-(14-readcount);
            parseReceivedData(valBuf);
        }

        if (potcount==14) {
            System.arraycopy(data, 0, readbuf, readcount, numBytesRead);
            parseReceivedData(readbuf);
            readcount = 0;
            Arrays.fill(readbuf, (byte) 0);
        }

    }

    /**
     * Starts the activity, using the supplied driver instance.
     *
     * @param context
     * @param driver
     */
    static void show(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, SerialConsoleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_changedetect) {
            chDetect=!chDetect; // toggle change detection
            return true;
        }

        if (id == R.id.action_resetvalues) {
            changed.clear();
            // plot.clear();
            return true;
        }

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


}


