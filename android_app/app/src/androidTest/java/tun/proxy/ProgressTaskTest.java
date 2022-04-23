package tun.proxy;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import tun.utils.ProgressTask;
import tun.utils.Util;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class ProgressTaskTest {
    private static final String TAG = "ProgressTaskTest";

    @Before
    public void setUp() {

    }

    @After
    public void tearDown() {

    }

    @Test
    public void progressTask() {
        Log.w(TAG, "progressTask: start");

        ProgressTask task = new ProgressTask<String, String, List<PackageInfo>>() {

            @Override
            protected List<PackageInfo> doInBackground(String... var1) {
                for (int i = 0; i < 100; i++) {
                    try {
                        Thread.sleep(10);
                        Log.d(TAG, "Progress:" + i);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }

        };

        Assert.assertEquals(task.getStatus(), ProgressTask.Status.PENDING);
        Log.w(TAG, "progressTask: execute");
        task.execute();
        Assert.assertEquals(task.getStatus(), ProgressTask.Status.RUNNING);

        Log.w(TAG, "progressTask: end");

    }
}
