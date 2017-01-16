package android.hardware;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.hardware.IControl;

/* Original code: */
/* https://github.com/johnzweng/XposedLePro3Infrared */

/**
 * @hide
 */
public class InfraredMagic {

	private static final String TAG = "TS-IRMagic";

    /**
     * Client API for the QuickSet control service:
     */
    private IControl mControl;

    /**
     * Store reference context of hooked ConsumerIrService (as we need it for binding a service)
     */
    private Context mContext;

    /**
     * Flag if we have successfully bound the QuickSet service
     */
    private boolean mBound = false;

	private boolean mIsAvailable = false;

    /**
     * Service Connection used to control the bound QuickSet SDK Service
     */
    private final ServiceConnection mControlServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBound = true;
            mControl = new IControl(service);
            Log.i(TAG, "QuickSet SDK Service (for controlling IR Blaster) SUCCESSFULLY CONNECTED! Yeah! :-)");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            mControl = null;
            Log.i(TAG, "QuickSet SDK Service (for controlling IR Blaster) DISCONNECTED!");
        }
    };

	public boolean hasIrEmitter() throws RemoteException {
		return mIsAvailable;
	}

	public final static int[] CONSUMERIR_CARRIER_FREQUENCIES = {30000, 30000, 33000, 33000, 36000, 36000, 38000, 38000, 40000, 40000, 56000, 56000};
	private final static String SYS_FILE_ENABLE_IR_BLASTER = "/sys/remote/enable";

	public int[] getCarrierFrequencies() throws RemoteException {
		return CONSUMERIR_CARRIER_FREQUENCIES;
	}

	/**
     * Try to enable IR emitter by writing a '1' into sys file
     * '/sys/remote/enable'
     */
    private static void writeOneToSysFile() {
        Log.d(TAG, "will try to do: 'echo 1 > " + SYS_FILE_ENABLE_IR_BLASTER + "'");
        try {
            File enableFile = new File(SYS_FILE_ENABLE_IR_BLASTER);
            if (!enableFile.exists()) {
                Log.w(TAG, "sys-file '" + SYS_FILE_ENABLE_IR_BLASTER + "' doesn't exist on this phone. Maybe this phone doesn't support this mechanism for IR blaster power-on?");
                return;
            }
            if (!enableFile.isFile()) {
				Log.w(TAG, "sys-file '" + SYS_FILE_ENABLE_IR_BLASTER + "' is not a file! Strange... ??");
                return;
            }
            if (!enableFile.canWrite()) {
                Log.w(TAG, "Sorry, we don't have permission to write into sys-file '" + SYS_FILE_ENABLE_IR_BLASTER + "'. Cannot enable IR Blaster. :-(");
                return;
            }
            FileWriter fileWriter = new FileWriter(enableFile);
            fileWriter.write("1");
            fileWriter.flush();
            fileWriter.close();
            Log.i(TAG, "Success! IR Blaster successfully enabled. Set '" + SYS_FILE_ENABLE_IR_BLASTER + "' to 1. :-)");
        } catch (IOException e1) {
            Log.e(TAG, "Exception when opening sys file " + SYS_FILE_ENABLE_IR_BLASTER + ". Cannot enable IR Blaster. :-(", e1);
        } catch (Throwable t1) {
            Log.e(TAG, "Throwable when opening sys file " + SYS_FILE_ENABLE_IR_BLASTER + ". Cannot enable IR Blaster. :-(\n" + t1.toString() + "\n" + t1.getMessage());
        }
    }

    /**
     * Try to send Infrared pattern, catch and log exceptions.
     *
     * @param carrierFrequency carrier frequency, see ConsumerIrManager Android API
     * @param pattern          IR pattern to send, see ConsumerIrManager Android API
     */
    public int transmit(String packageName, int carrierFrequency, int[] pattern) throws RemoteException {

        Log.d(TAG, "transmit ("+packageName+") called: freq: " + carrierFrequency + ", pattern-len: " + pattern.length);

        if (mControl == null || !mBound) {
            Log.w(TAG, "QuickSet Service (for using IR Blaster) seems not to be bound. Trying to bind again and exit.");
            bindQuickSetService();
            // return something != 0 to indicate error
            return 999;
        }
        try {
            mControl.transmit(carrierFrequency, pattern);
            int resultCode = mControl.getLastResultcode();
            if (resultCode != 0) {
                Log.w(TAG, "resultCode after calling transmit on QuickSet SDK was != 0. No idea what this means. lastResultcode: " + resultCode);
            }
            return resultCode;
        } catch (Throwable t) {
            Log.e(TAG, "Exception while trying to send command to QuickSet Service. :-(", t);
            // return something != 0 to indicate error
            return 999;
        }
    }

    /**
     * Try to bind QuickSet SDK Service
     */
    public void bindQuickSetService() {
        Log.d(TAG, "Trying to bind QuickSet service (for controlling IR Blaster): " + IControl.QUICKSET_UEI_PACKAGE_NAME + " - " + IControl.QUICKSET_UEI_SERVICE_CLASS);
        if (mContext == null) {
            Log.w(TAG, "Cannot bind QuickSet control service (now), as context is null. :-(");
            return;
        }
        try {
            Intent controlIntent = new Intent(IControl.ACTION);
            controlIntent.setClassName(IControl.QUICKSET_UEI_PACKAGE_NAME, IControl.QUICKSET_UEI_SERVICE_CLASS);
            boolean bindResult = mContext.bindService(controlIntent, mControlServiceConnection, Context.BIND_AUTO_CREATE);
            if (!bindResult) {
				mIsAvailable = false;
                Log.e(TAG, "bindResult == false. QuickSet SDK service seems NOT TO BE AVAILABLE ON THIS PHONE! IR Blaster will probably NOT WORK!");
                Log.e(TAG, "QuickSet SDK service package/class: " + IControl.QUICKSET_UEI_PACKAGE_NAME + "/" + IControl.QUICKSET_UEI_SERVICE_CLASS);
            } else {
				mIsAvailable = true;
                Log.d(TAG, "bindService() result: true");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Binding QuickSet Control service failed with exception :-(", t);
        }
    }

    /**
     * Store reference to context
     *
     * @param ctx context
     */
    public void setContext(Context ctx) {
		writeOneToSysFile();
        this.mContext = ctx;
    }

}
