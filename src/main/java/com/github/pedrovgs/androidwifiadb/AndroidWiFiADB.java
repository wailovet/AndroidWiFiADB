/*
 * Copyright (C) 2015 Pedro Vicente Gómez Sánchez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.pedrovgs.androidwifiadb;

import com.github.pedrovgs.androidwifiadb.adb.ADB;
import com.github.pedrovgs.androidwifiadb.view.View;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class AndroidWiFiADB {

    private static final Set<Device> DEVICES = new HashSet<Device>();
    private final ADB adb;
    private final View view;

    public AndroidWiFiADB(ADB adb, View view) {
        this.adb = adb;
        this.view = view;
    }

    public void syncAllIp() {
        String[] ip_arr = {"192", "168", "1", "0"};
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            ip_arr = ip.split("[.]");
        } catch (Exception e) {
            e.printStackTrace();
        }
        final int[] thread_count = {0};
        for (int i = 1; i < 255; i++) {
            ip_arr[3] = String.valueOf(i);

            String[] finalIp_arr = ip_arr;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String ip = StringUtils.join(finalIp_arr, ".");
                    thread_count[0]++;
                    try {
                        Socket socket = new Socket();
                        socket.connect(new InetSocketAddress(ip, 5555), 500);
                        socket.close();
                        Device d = new Device(ip,ip);
                        d.setIp(ip);
                        d.setConnected(true);
                        DEVICES.add(d);
                        System.out.println(ip + "OK");
                    } catch (Exception e) {
                        System.out.println(ip + "ERROR");
                        System.out.println(e.getMessage());
                    }

                    thread_count[0]--;

                }
            }).start();

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        while (thread_count[0] > 0){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


    }

    public void connectDevices() {
        if (!isADBInstalled()) {
            view.showADBNotInstalledNotification();
            return;
        }
        DEVICES.clear();
        DEVICES.addAll(adb.getDevicesConnectedByUSB());
        syncAllIp();

        if (DEVICES.isEmpty()) {
            view.showNoConnectedDevicesNotification();
            return;
        }

        DEVICES.addAll(adb.connectDevices(DEVICES));
        showConnectionResultNotification(DEVICES);
    }

    public boolean refreshDevicesList() {
        if (!isADBInstalled()) {
            return false;
        }
        removeNotConnectedDevices();
        final Collection<Device> connected = adb.getDevicesConnectedByUSB();


//        syncAllIp();

        for (Device connectedDevice : connected) {
            if (!checkDeviceExistance(connectedDevice)) {
                connectedDevice.setIp(adb.getDeviceIp(connectedDevice));
                DEVICES.add(connectedDevice);
            } else {
                updateDeviceConnectionState(connectedDevice);
            }
        }
        return true;
    }

    private void removeNotConnectedDevices() {
        List<Device> connectedDevices = new LinkedList<Device>();
        for (Device device : DEVICES) {
            if (device.isConnected()) {
                connectedDevices.add(device);
            }
        }
        DEVICES.clear();
        DEVICES.addAll(connectedDevices);
    }

    public Collection<Device> getDevices() {
        return DEVICES;
    }

    public void connectDevice(Device device) {
        if (!isADBInstalled()) {
            view.showADBNotInstalledNotification();
            return;
        }

        Collection<Device> connectedDevices = new ArrayList<Device>();
        connectedDevices.add(device);
        connectedDevices = adb.connectDevices(connectedDevices);
        for (Device connected : connectedDevices) {
            updateDeviceConnectionState(connected);
        }
        showConnectionResultNotification(connectedDevices);
    }

    public void disconnectDevice(Device device) {
        if (!isADBInstalled()) {
            view.showADBNotInstalledNotification();
            return;
        }

        List<Device> disconnectedDevices = new ArrayList<Device>();
        disconnectedDevices.add(device);
        disconnectedDevices = adb.disconnectDevices(disconnectedDevices);
        for (Device disconnected : disconnectedDevices) {
            updateDeviceConnectionState(disconnected);
        }
        showDisconnectionResultNotification(disconnectedDevices);
    }

    public void updateProject(Project project) {
        adb.updateProject(project);
    }

    private void updateDeviceConnectionState(final Device updatedDevice) {
        DEVICES.add(updatedDevice);
    }

    private boolean checkDeviceExistance(Device connectedDevice) {
        boolean deviceExists = false;
        for (Device device : DEVICES) {
            if (connectedDevice.getId().equals(device.getId())) {
                deviceExists = true;
            }
        }
        return deviceExists;
    }

    private boolean isADBInstalled() {
        return adb.isInstalled();
    }

    private void showConnectionResultNotification(Collection<Device> devices) {
        for (Device device : devices) {
            if (device.isConnected()) {
                view.showConnectedDeviceNotification(device);
            } else {
                view.showErrorConnectingDeviceNotification(device);
            }
        }
    }

    private void showDisconnectionResultNotification(Collection<Device> devices) {
        for (Device device : devices) {
            if (!device.isConnected()) {
                view.showDisconnectedDeviceNotification(device);
            } else {
                view.showErrorDisconnectingDeviceNotification(device);
            }
        }
    }
}
