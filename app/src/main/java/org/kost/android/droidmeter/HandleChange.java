package org.kost.android.droidmeter;

import java.util.LinkedList;

/**
 * Created by vkosturj on 10.09.16..
 */
public class HandleChange {
    LinkedList<Float> values;
    Integer MaxSize;
    Float Min, Max;
    Integer lastState;

    public HandleChange(Integer maxsize) {
        values=new LinkedList<Float>();
        MaxSize=maxsize;
        Min=null;
        Max=null;
        lastState=null;
    }

    public void add(Float value) {
        if (values.size()+1>MaxSize) {
            values.removeFirst();
        }
        values.addLast(value);
        if (Min==null || Min>value) {
            Min=value;
        }
        if (Max==null || Max<value) {
            Max=value;
        }
    }

    public Float average() {
        Float sum=Float.valueOf(0);
        Float avg=Float.valueOf(0);
        if (values.size()>0) {
            for (int i=0; i<values.size(); i++) {
                sum=sum+values.get(i);
            }
            avg=sum/values.size();
        }
        return avg;
    }

    public void clear() {
        if (values.size()>0) {
            for (int i = 0; i < values.size(); i++) {
                values.removeFirst();
            }
        }
        Min=null;
        Max=null;
    }

    public boolean isFull() {
        if (values.size()>=MaxSize) {
            return true;
        }
        return false;
    }
    
    public boolean isChange() {
        if (!isFull()) {
            return false;
        }
        if (lastState==null) {
            lastState=1;
            return false;
        }

        Float avg=average();
        Float diff=(Max-Min)/2;

        if (lastState==1 && avg>Min+diff) {
            lastState=2;
            return true;
        }
        if (lastState==2 && avg<Max-diff) {
            lastState=1;
            return true;
        }
        return false;
    }
}
