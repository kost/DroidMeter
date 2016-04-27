package org.kost.android.droidmeter;

import android.util.Log;

import java.util.Arrays;

/**
 * Created by vkosturj on 26.04.16..
 */
public class ut61pkt {
    String acdc="UN";
    String mtype="";
    String munit="";
    String mmode="";
    float value;
    float readvalue;
    int exp;

    public ut61pkt(byte[] pkt) {
        parseut61(pkt);
    }

    public String toStr () {
        String str = "Value: "+String.valueOf(value)+" (mmode: "+mmode+" | mtype: "+mtype+" | Current: "+acdc+" | munit: "+munit+" )";
        return str;
    }

    public String toStrDebug () {
        String str = "Value: "+String.valueOf(value)+" with exp: "+String.valueOf(exp)+" and readvalue: "+String.valueOf(readvalue)+" mmode: "+mmode+" | mtype: "+mtype+" | Current: "+acdc+" | munit: "+munit+" )";
        return str;
    }

    public String toCSV () {
        String str=String.valueOf(value)+";"+mmode+";"+mtype+";"+acdc+";"+munit;
        return str;
    }

    public static boolean hasBit(byte src, int position) {
        if (((src >> (byte)7-(byte)position) & (byte)1)!=0) {
        //if ((src & ((byte)1 << (byte)position)) != 0) {
            return true;
        }
        return false;
    }

    public void parseut61(byte[] pkt) {

        // System.out.println("\nBegin\n");
        // System.out.println(Arrays.toString(pkt));

        readvalue=0;

        if (pkt[1] == '?') {
            readvalue = 9999;
        } else {
            byte[] byteval= new byte[] {pkt[1], pkt[2], pkt[3], pkt[4]};
            String strval= new String(byteval);
            readvalue=Integer.parseInt(strval);
        }

        switch(pkt[6]) {
            case '0':
                exp=1;
                break;
            case '1':
                exp=1000;
                break;
            case '2':
                exp=100;
                break;
            case '4':
                exp=10;
                break;
            default:
                exp=1;
        }

        if (pkt[0]=='-') {
            exp=exp*-1;
        }

        value=readvalue/exp;

        if (hasBit(pkt[7],3)) {
            acdc="DC";
        }
        if (hasBit(pkt[7],4)) {
            acdc="AC";
        }

        if (hasBit(pkt[8],6)) {
            mtype="n";
        }
        if (hasBit(pkt[9],0)) {
            mtype="u";
        }
        if (hasBit(pkt[9],1)) {
            mtype="m";
        }
        if (hasBit(pkt[9],2)) {
            mtype="k";
        }
        if (hasBit(pkt[9],3)) {
            mtype="M";
        }

        if (hasBit(pkt[9], 6)) {
            munit = "%";
        }
        if (hasBit(pkt[10], 0)) {
            munit = "V";
        }
        if (hasBit(pkt[10], 1)) {
            munit = "A";
        }
        if (hasBit(pkt[10], 2)) {
            munit = "Ohm";
        }
        if (hasBit(pkt[10], 4)) {
            munit = "Hz";
        }
        if (hasBit(pkt[10], 5)) {
            munit = "F";
        }
        if (hasBit(pkt[10], 6)) {
            munit = "oC";
        }
        if (hasBit(pkt[10], 7)) {
            munit = "oF";
        }

        String bits="";
        for (int i=0; i < 8; i++) {
            if (hasBit(pkt[7],i))
                bits=bits+"1";
            else
                bits=bits+"0";

        }

        Log.i("blah","7 byte - bits: "+bits);

        mmode="manual";
        if (hasBit(pkt[7], 2)) {
            mmode = "auto";
        }

        // System.out.printf("Value: %f, Exp: %d, RealValue: %f (%s current) (%s mtype) (%s unit) (%s mmode)",readvalue,exp,value,acdc, mtype, munit, mmode);
        // System.out.println("\nEnd\n");
    }
}
