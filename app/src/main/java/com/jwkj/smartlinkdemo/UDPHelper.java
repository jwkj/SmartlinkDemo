package com.jwkj.smartlinkdemo;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;

public class UDPHelper {
    public Boolean IsThreadDisable = false;
    private final static String UDPHelper = "UDPHelper";
    public int port;
    InetAddress mInetAddress;
    public Handler mHandler;
    MulticastSocket datagramSocket = null;
    public static final int HANDLER_MESSAGE_BIND_ERROR = 0x01;
    public static final int HANDLER_MESSAGE_RECEIVE_MSG = 0x02;
    private WeakReference<Context> mActivityReference;
    private WifiManager.MulticastLock lock;
	private boolean isStartSuccess=false;

    public UDPHelper(Context mContext, int port) {
        this.port = port;
        mActivityReference = new WeakReference<>(mContext);
        WifiManager manager = (WifiManager) mActivityReference.get().getSystemService(Context.WIFI_SERVICE);
        lock = manager.createMulticastLock(UDPHelper);
    }

    public void setCallBack(Handler handler) {
        this.mHandler = handler;
    }

    //部分手机此处开始监听时会报异常，所以循环尝试开始监听
    public void StartListen() {
        new Thread() {
            @Override
            public void run() {
                isStartSuccess=false;
                while (!isStartSuccess){
                    listen();
                    try {
                        sleep(800);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    public void listen() {
        // 接收的字节大小，客户端发送的数据不能超过这个大小
        byte[] message = new byte[1024];
        try {
            // 建立Socket连接
            try {
                datagramSocket = new MulticastSocket(port);
            } catch (Exception e) {
                port = 57521;
                datagramSocket = new MulticastSocket(port);
                e.printStackTrace();
            }
            datagramSocket.setBroadcast(true);
            datagramSocket.setSoTimeout(120*1000);
            DatagramPacket datagramPacket = new DatagramPacket(message,
                    message.length);
			isStartSuccess=true;
            while (!IsThreadDisable) {
                // 准备接收数据
                MulticastLock();
                datagramSocket.receive(datagramPacket);
                mInetAddress = datagramPacket.getAddress();
                byte[] data = datagramPacket.getData();
                int subType =0;
                int contactId = bytesToInt(data, 16);
                int rflag = bytesToInt(data, 12);
                int type = bytesToInt(data, 20);
                int frag = bytesToInt(data, 24);
                int curVersion = (rflag >> 4) & 0x1;
                if (curVersion == 1) {
                    subType = bytesToInt(data, 80);
                }
                if (data[0] == 1) {
                    if (null != mHandler) {
                        Message msg = new Message();
                        msg.what = HANDLER_MESSAGE_RECEIVE_MSG;
                        Bundle bundler = new Bundle();
                        bundler.putString("contactId",
                                String.valueOf(contactId));
                        bundler.putString("frag", String.valueOf(frag));
                        String ip_address = mInetAddress.getHostAddress();
                        bundler.putString(
                                "ipFlag",
                                ip_address.substring(
                                        ip_address.lastIndexOf(".") + 1,
                                        ip_address.length()));
                        bundler.putString("ip", ip_address);
                        bundler.putInt("type", type);
                        bundler.putInt("rflag", rflag);
                        bundler.putInt("subType", subType);
                        msg.setData(bundler);
                        mHandler.sendMessage(msg);
                        break;
                    }
                }
                MulticastUnLock();
            }
        } catch (SocketException e) {
            e.printStackTrace();
            //如果是此异常，isStartSuccess没有置为true，还会重新监听，所以这里不处理
        } catch (Exception e) {
            e.printStackTrace();
            IsThreadDisable = true;
            if (null != mHandler) {
                mHandler.sendEmptyMessage(HANDLER_MESSAGE_BIND_ERROR);
            }
        } finally {
            MulticastUnLock();
            if (null != datagramSocket) {
                datagramSocket.close();
                datagramSocket = null;
            }
        }
    }

    public static int bytesToInt(byte[] src, int offset) {
        int value;
        value = (src[offset] & 0xFF) | ((src[offset + 1] & 0xFF) << 8)
                | ((src[offset + 2] & 0xFF) << 16) | ((src[offset + 3] & 0xFF) << 24);
        return value;
    }

    public void StopListen() {
        this.IsThreadDisable = true;
        this.isStartSuccess=true;
        if (null != datagramSocket) {
            datagramSocket.close();
            datagramSocket = null;
        }
    }

    private void MulticastLock() {
        if (this.lock != null) {
            try {
                this.lock.acquire();
            } catch (Exception e) {
                Log.e("SDK", "MulticastLock error");
            }
        }
    }

    private void MulticastUnLock() {
        if (this.lock != null) {
            try {
                this.lock.release();
            } catch (Exception e) {
                Log.e("SDK", "MulticastUnLock error");
            }
        }
    }
}
