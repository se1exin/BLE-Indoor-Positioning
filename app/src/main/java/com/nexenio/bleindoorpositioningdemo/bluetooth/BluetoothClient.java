package com.nexenio.bleindoorpositioningdemo.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.util.Log;

import com.nexenio.bleindoorpositioning.ble.advertising.AdvertisingPacket;
import com.nexenio.bleindoorpositioning.ble.beacon.Beacon;
import com.nexenio.bleindoorpositioning.ble.beacon.BeaconManager;
import com.nexenio.bleindoorpositioning.ble.beacon.Eddystone;
import com.nexenio.bleindoorpositioning.ble.beacon.IBeacon;
import com.nexenio.bleindoorpositioning.location.Location;
import com.nexenio.bleindoorpositioning.location.provider.EddystoneLocationProvider;
import com.nexenio.bleindoorpositioning.location.provider.IBeaconLocationProvider;
import com.polidea.rxandroidble.RxBleClient;
import com.polidea.rxandroidble.scan.ScanResult;
import com.polidea.rxandroidble.scan.ScanSettings;

import java.util.Arrays;

import rx.Observer;
import rx.Subscription;

/**
 * Created by steppschuh on 24.11.17.
 */

public class BluetoothClient {

    private static final String TAG = BluetoothClient.class.getSimpleName();
    public static final int REQUEST_CODE_ENABLE_BLUETOOTH = 10;

    private static BluetoothClient instance;

    private Context context;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BeaconManager beaconManager = BeaconManager.getInstance();

    private RxBleClient rxBleClient;
    private Subscription scanningSubscription;

    private BluetoothClient() {

    }

    public static BluetoothClient getInstance() {
        if (instance == null) {
            instance = new BluetoothClient();
        }
        return instance;
    }

    public static void initialize(@NonNull Context context) {
        Log.v(TAG, "Initializing with context: " + context);
        BluetoothClient instance = getInstance();
        instance.rxBleClient = RxBleClient.create(context);
        instance.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        instance.bluetoothAdapter = instance.bluetoothManager.getAdapter();
        if (instance.bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter is not available");
        }
    }

    public static void startScanning() {
        if (isScanning()) {
            return;
        }

        final BluetoothClient instance = getInstance();
        Log.d(TAG, "Starting to scan for beacons");

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build();

        instance.scanningSubscription = instance.rxBleClient.scanBleDevices(scanSettings)
                .subscribe(new Observer<ScanResult>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "Bluetooth scanning error", e);
                    }

                    @Override
                    public void onNext(ScanResult scanResult) {
                        instance.processScanResult(scanResult);
                    }
                });
    }

    public static void stopScanning() {
        if (!isScanning()) {
            return;
        }

        BluetoothClient instance = getInstance();
        Log.d(TAG, "Stopping to scan for beacons");
        instance.scanningSubscription.unsubscribe();
    }

    public static boolean isScanning() {
        Subscription subscription = getInstance().scanningSubscription;
        return subscription != null && !subscription.isUnsubscribed();
    }

    public static boolean isBluetoothEnabled() {
        BluetoothClient instance = getInstance();
        return instance.bluetoothAdapter != null && instance.bluetoothAdapter.isEnabled();
    }

    public static void requestBluetoothEnabling(@NonNull Activity activity) {
        Log.d(TAG, "Requesting bluetooth enabling");
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activity.startActivityForResult(enableBtIntent, REQUEST_CODE_ENABLE_BLUETOOTH);
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private void processScanResult(@NonNull ScanResult scanResult) {
        String tileUUIDUnPaired = "0000feec-0000-1000-8000-00805f9b34fb";
        String tileUUIDPaired = "0000feed-0000-1000-8000-00805f9b34fb";
        // TILE 1: C5:B5:FE:7D:6C:4D
        //  UUID: 0000feec-0000-1000-8000-00805f9b34fb
        //  UUID: 0000feed-0000-1000-8000-00805f9b34fb

        // TILE 2: C2:D8:B7:E9:53:7F
        //  UUID: 0000feec-0000-1000-8000-00805f9b34fb

        // TILE 3: E4:E9:DD:55:3A:91

        // TILE 4: F4:16:7F:AE:44:42

        String uuid = "";
        String macAddress = scanResult.getBleDevice().getMacAddress();
        String name = scanResult.getBleDevice().getName();
        byte[] data = scanResult.getScanRecord().getBytes();

        AdvertisingPacket advertisingPacket = BeaconManager.processAdvertisingData(macAddress, data, scanResult.getRssi());

        if (advertisingPacket != null) {
            Log.d("TESTTEST", "FOUND: " + name + ": " + macAddress + ": " + uuid + " RSSI: " + scanResult.getRssi() + " TX: " + scanResult.getScanRecord().getTxPowerLevel());
            Beacon beacon = BeaconManager.getBeacon(macAddress, advertisingPacket.getBeaconClass());
            if (beacon instanceof IBeacon && !beacon.hasLocation()) {
                beacon.setLocationProvider(createDebuggingLocationProvider((IBeacon) beacon));
            }

            if (beacon instanceof Eddystone && !beacon.hasLocation()) {
                beacon.setLocationProvider(createDebuggingTileLocationProvider((Eddystone) beacon));
            }
        }
    }

    private static EddystoneLocationProvider<Eddystone> createDebuggingTileLocationProvider(Eddystone beacon) {
        final Location beaconLocation = new Location();

        // Tile 1
        if (beacon.getMacAddress().equals("C5:B5:FE:7D:6C:4D")) {
            beaconLocation.setLatitude(-33.867364);
            beaconLocation.setLongitude(151.189279);
            beaconLocation.setAltitude(10);
        }

        // Tile 2
        if (beacon.getMacAddress().equals("C2:D8:B7:E9:53:7F")) {
            beaconLocation.setLatitude(-33.867330);
            beaconLocation.setLongitude(151.189364);
            beaconLocation.setAltitude(10);
        }

        // Tile 3
        if (beacon.getMacAddress().equals("E4:E9:DD:55:3A:91")) {
            beaconLocation.setLatitude(-33.867270);
            beaconLocation.setLongitude(151.189317);
            beaconLocation.setAltitude(10);
        }

        // Tile 4
        if (beacon.getMacAddress().equals("F4:16:7F:AE:44:42")) {
            beaconLocation.setLatitude(-33.867284);
            beaconLocation.setLongitude(151.189219);
            beaconLocation.setAltitude(10);
        }


        return new EddystoneLocationProvider<Eddystone>(beacon) {
            @Override
            public void updateLocation() {
                this.location = beaconLocation;
            }
        };
    }

    private static IBeaconLocationProvider<IBeacon> createDebuggingLocationProvider(IBeacon iBeacon) {
        final Location beaconLocation = new Location();
        switch (iBeacon.getMinor()) {
            case 1: {
                beaconLocation.setLatitude(52.512437);
                beaconLocation.setLongitude(13.391124);
                beaconLocation.setAltitude(36);
                break;
            }
            case 2: {
                beaconLocation.setLatitude(52.512411788476356);
                beaconLocation.setLongitude(13.390875654442985);
                beaconLocation.setElevation(2.65);
                beaconLocation.setAltitude(36);
                break;
            }
            case 3: {
                beaconLocation.setLatitude(52.51240486636751);
                beaconLocation.setLongitude(13.390770270005437);
                beaconLocation.setElevation(2.65);
                beaconLocation.setAltitude(36);
                break;
            }
            case 4: {
                beaconLocation.setLatitude(52.512426);
                beaconLocation.setLongitude(13.390887);
                beaconLocation.setElevation(2);
                beaconLocation.setAltitude(36);
                break;
            }
            case 5: {
                beaconLocation.setLatitude(52.512347534813834);
                beaconLocation.setLongitude(13.390780437281524);
                beaconLocation.setElevation(2.9);
                beaconLocation.setAltitude(36);
                break;
            }
            case 12: {
                beaconLocation.setLatitude(52.51239708899507);
                beaconLocation.setLongitude(13.390878261276518);
                beaconLocation.setElevation(2.65);
                beaconLocation.setAltitude(36);
                break;
            }
            case 13: {
                beaconLocation.setLatitude(52.51242692608082);
                beaconLocation.setLongitude(13.390872969910035);
                beaconLocation.setElevation(2.65);
                beaconLocation.setAltitude(36);
                break;
            }
            case 14: {
                beaconLocation.setLatitude(52.51240825552749);
                beaconLocation.setLongitude(13.390821867681456);
                beaconLocation.setElevation(2.65);
                beaconLocation.setAltitude(36);
                break;
            }
            case 15: {
                beaconLocation.setLatitude(52.51240194910502);
                beaconLocation.setLongitude(13.390725856632926);
                beaconLocation.setElevation(2.65);
                beaconLocation.setAltitude(36);
                break;
            }
            case 16: {
                beaconLocation.setLatitude(52.512390301005595);
                beaconLocation.setLongitude(13.39077285305359);
                beaconLocation.setElevation(2.65);
                beaconLocation.setAltitude(36);
                break;
            }
            case 17: {
                beaconLocation.setLatitude(52.51241817994876);
                beaconLocation.setLongitude(13.390767908948872);
                beaconLocation.setElevation(2.65);
                beaconLocation.setAltitude(36);
                break;
            }
            case 18: {
                beaconLocation.setLatitude(52.51241494408066);
                beaconLocation.setLongitude(13.390923696709294);
                beaconLocation.setElevation(2.65);
                beaconLocation.setAltitude(36);
                break;
            }
        }
        return new IBeaconLocationProvider<IBeacon>(iBeacon) {
            @Override
            public void updateLocation() {
                this.location = beaconLocation;
            }
        };
    }

}
