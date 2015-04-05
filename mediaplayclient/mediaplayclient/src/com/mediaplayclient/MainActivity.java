package com.mediaplayclient;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

public class MainActivity extends Activity {
	private String serverUrl="http://123.57.39.141/test/android/GetServerData?";
	private String rootUrl="http://123.57.39.141/test/android/DownloadMedia?";
	String downloadDir="/mnt/sdcard/medias/";
	private VideoView myVideoView; // 声明一个变量和页面的元素对应起来			
	private TextView mTime; // 显示时间的
	private TextView tvSubtitle;//显示字幕
	private ImageView apkIcon;//显示图标
    private String sn;
	private SQLiteDatabase db1;
	private DBHelper dbhelper;

	List<MediaInfo> lstMedia = new ArrayList<MediaInfo>();// 存放正在播放的影片信息
	private int mediaindex = 0;
	
	private int internalSendSn=10000;//10s
	private int internalRequestMedia=100000;//100s
	
	private static final int msgKey1 = 1;//获取本设备的时间信息
	private static final int msgKey2 = 2;//获取服务器的时间信息
	private static final int msgKey3 = 3;//程序启动后，首次发送json信息到服务器
	private static final int msgKey4 = 4;//程序间隔100s，请求影片列表
	private static final int msgKey5 = 5;//下载影片
	
	boolean IsMediaUpdate=false;
	
	
	Context mContext;  
    DownloadManager manager ;  
    DownloadCompleteReceiver receiver;  
	
	private Handler handler = new Handler() {
		@Override
		// 当有消息发送出来的时候就执行Handler的这个方法
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {			
			case msgKey1:
				Log.i("mch", "get eq time");			
				long sysTime = System.currentTimeMillis();
				CharSequence sysTimeStr = DateFormat.format("hh:mm:ss", sysTime);
				mTime.setText(sysTimeStr);
				handler.sendEmptyMessageDelayed(msgKey1, 1000);
				break;
			case msgKey2:
				Log.i("mch", "get server time");
				break;
			case msgKey3:
				Log.i("mch", "send sn start");
				SendEqInfo();		
				Log.i("mch", "send sn end");
				handler.sendEmptyMessageDelayed(msgKey3, internalSendSn);
				break;
			case msgKey4:
				Log.i("mch", "Request media list");
				GetMyMediaList();
				if(IsMediaUpdate){
					lstMedia=GetLocalMedia();
					if(myVideoView.isPlaying())
						myVideoView.stopPlayback();
					PlayMovies();
				}

				//handler.sendEmptyMessageDelayed(msgKey4,internalRequestMedia); // 循环：每播完一个请求一次列表。
				break;
			case msgKey5:
				Log.i("mch", "download media list");				
				break;
			default:
				break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		apkIcon = (ImageView) findViewById((R.id.imgApkIcon));
		apkIcon.setImageDrawable(getResources().getDrawable(
				R.drawable.logo));
		
		// 处理时间
		mTime = (TextView) findViewById(R.id.tvTime);
		handler.sendEmptyMessageAtTime(msgKey1, 1000);

		myVideoView = (VideoView) findViewById(R.id.myVideoView);
		tvSubtitle = (TextView) findViewById(R.id.tvSubtitle);
		//tvSubtitle.setText("华硕电脑是全球消费型笔记本电脑全球第三大,主板全球第一的厂商.拥有世界级研发团队");
		//tvSubtitle.requestFocus();
		
		//程序启动后，发送sn到服务器
		sn = android.provider.Settings.System.getString(
				getContentResolver(), "android_id");	
		Log.i("download address", sn);
		try{
		handler.sendEmptyMessageAtTime(msgKey3, 10000);
		}
		catch(Exception ex)
		{			
			Log.i("download address", "ex1 send sn");
		}
				
		//第二 发送视频列表请求
		try{
		handler.sendEmptyMessageAtTime(msgKey4, 1000);
		}
		catch(Exception ex)
		{			
			Log.i("download address", "ex2 get list");
		}
		
		try{
			GetMyMediaList();
		}
		catch(Exception e)
		{
			
		}
		
				
		//第三循环播放视频列表
		lstMedia=GetLocalMedia();
		try{
		downLoadMovies();
		}
		catch(Exception ex)
		{
			Log.i("download address", "ex3 download list");
		}
		PlayMovies();
        
		// String s1=getLocalIpAddress();
		// String s2 = getLocalMacAddress();
	}

	// 播放视频列表
	public void PlayMovies() {
		
		if(lstMedia.size()>0)
		{
		String videoPath=downloadDir+lstMedia.get(mediaindex).MediaUrl;
		Log.i("download address", videoPath+"_"+mediaindex);
		File file = new File(videoPath);
		if (!file.exists()) {		
			mediaindex=mediaindex+1;
			return;
		}
		myVideoView.setVideoPath(file.getAbsolutePath());		
		Log.i("download address", "ssss");
		}
		myVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
				mp.start();				
				Log.i("download address", "start");
				tvSubtitle.setText(lstMedia.get(mediaindex).Description);
				tvSubtitle.requestFocus();
				mediaindex = mediaindex + 1;
			}
		});

		myVideoView
				.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
					@Override
					public void onCompletion(MediaPlayer mp) {
						handler.sendEmptyMessageAtTime(msgKey4, 1000);
						if (lstMedia.size() > mediaindex) {					
							String videoPath=downloadDir+lstMedia.get(mediaindex).MediaUrl;
							Log.i("download address", videoPath);
							File file = new File(videoPath);
							if (!file.exists()) {
								mediaindex=mediaindex+1;
								return;
							}
							myVideoView.setVideoPath(file.getAbsolutePath());
						} else {
							mediaindex = 0;
							File file = new File(downloadDir+lstMedia.get(mediaindex).MediaUrl);
							if (!file.exists()) {
								mediaindex=mediaindex+1;
								return;
							}
							myVideoView.setVideoPath(file.getAbsolutePath());
						}
						myVideoView.start();

					}
				});

	}

	// 获取mac地址
	public String getLocalMacAddress() {
		WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		WifiInfo info = wifi.getConnectionInfo();
		return info.getMacAddress();
	}

	// 获取ip地址
	public String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						return inetAddress.getHostAddress().toString();
					}
				}
			}
		} catch (SocketException ex) {
			Log.e("WifiPreference IpAddress", ex.toString());
		}
		return null;
	}

	// 发送设备信息，同时返回播放的档案信息
	public void SendEqInfo() {
		HttpClient httpClient = new DefaultHttpClient(); 
        try { 
        String url=serverUrl+"sn="+sn+"&req=1";
        HttpGet httpGet = new  HttpGet(url);        
        HttpResponse response = httpClient.execute(httpGet);
        } catch (Exception e) { 
            throw new RuntimeException(e); 
        }
		
	}

	// 发送消息，查看media是否变化
	public void GetMyMediaList() {
		HttpClient httpClient = new DefaultHttpClient(); 
        try { 
        String url=serverUrl+"sn="+sn+"&req=2";
        HttpGet httpGet = new  HttpGet(url); 
               
        HttpResponse response = httpClient.execute(httpGet);
        int res=response.getStatusLine().getStatusCode();
        if(res==200){
        	StringBuilder builder = new StringBuilder();
        	BufferedReader bufferedReader2 = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        	for (String s = bufferedReader2.readLine(); s != null; s = bufferedReader2
                .readLine()) {
            builder.append(s);
        	}
            Log.i("mch", builder.toString());
            if(builder.toString()!=""){
            	GetEqMediaData(builder.toString());
            	}
        	}
        } catch (Exception e) { 
            throw new RuntimeException(e); 
        }
        
	}

	// 接收返回的播放档案
	private void GetEqMediaData(String json) throws JSONException {
		
		JSONObject jsonObject = new JSONObject(json);
				
		JSONArray jsonArray = new JSONArray(jsonObject.getString("Data"));// 里面有一个数组数据，可以用getJSONArray获取数组		
		List<MediaInfo> lst=new ArrayList<MediaInfo>();
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject item = jsonArray.getJSONObject(i); // 得到每个对象
			//int id = item.getInt("id"); // 获取对象对应的值
			MediaInfo model=new MediaInfo();
			model.MediaName =  item.getString("MediaName");
			model.MediaUrl = item.getString("MediaUrl");
			model.Description = item.getString("SubTitle");
			 Log.i("mch json", model.MediaName);
			lst.add(model);			
		}		
		if(lstMedia.size()==0)
			lstMedia=GetLocalMedia();
		if(IsMediaListUpdate(lstMedia,lst)){
			SaveData(lst);
			IsMediaUpdate=true;
		}
		
	}

	//保存数据
	private void SaveData(List<MediaInfo> lst) {
		dbhelper = new DBHelper(this, "dbmedia.db", null, 1);
		db1 = this.openOrCreateDatabase("dbmedia.db", Context.MODE_PRIVATE,
				null);
		if (dbhelper.tabIsExist("tbmedia")) {
			db1.execSQL("drop table tbmedia");
			}
		db1.execSQL("CREATE TABLE tbmedia(id integer primary key autoincrement, MediaName varchar(50),MediaUrl varchar(500),Description varchar(2000))");
		for(int i=0;i<lst.size();i++)
		{
			MediaInfo model=lst.get(i);
			ContentValues cv = new ContentValues();
			cv.put("medianame", model.MediaName);
			cv.put("mediaurl", model.MediaUrl);
			cv.put("description", model.Description);
			db1.insert("tbmedia", "medianame,mediaurl,description", cv); // 插入数据
			
			Log.i("mch save data", model.MediaName);
		}
	}

	// 从数据库里查询自己要播放的视频，进行播放
	private List<MediaInfo> GetLocalMedia() {		
		List<MediaInfo> lst=new ArrayList<MediaInfo>();
		dbhelper = new DBHelper(this, "dbmedia.db", null, 1);
		db1 = this.openOrCreateDatabase("dbmedia.db", Context.MODE_PRIVATE,
				null);
		if (dbhelper.tabIsExist("tbmedia")) {				
			Cursor c = db1.rawQuery("select * from tbmedia",null);// 查询并获得游标
			while (c.moveToNext()) {
				
				MediaInfo model=new MediaInfo();
				model.MediaName = c.getString(c.getColumnIndex("MediaName"));
				model.MediaUrl = c.getString(c.getColumnIndex("MediaUrl"));
				model.Description = c.getString(c.getColumnIndex("Description"));
				lst.add(model);
				Log.i("mch local data", model.MediaName);
			}		
			c.close();
			db1.close();
		}
		return lst;

	}

	/* 通过新旧影片列表的对比，如果有变化，则返回true，否则false
	lst1 表示是正在播放的列表
	 * */
	private boolean IsMediaListUpdate(List<MediaInfo> lst1,List<MediaInfo> lst2){
		if(lst1.size()!=lst2.size())
			return true;
		
		int count=lst1.size();
		for(int i=0;i<count;i++)
		{
			MediaInfo m1=lst1.get(i);
			MediaInfo m2=lst2.get(i);
			if(m1.MediaName!=m2.MediaName || m1.MediaUrl!=m2.MediaUrl || m1.Description!=m2.Description)
			{
				return true;
			}
			
		}
		return false;		
	}
	
	private void downLoadMovies() {		
		int count=lstMedia.size();
		for(int i=0;i<count;i++)
		{
			String videoPath=downloadDir+lstMedia.get(i).MediaUrl;
			File file = new File(videoPath);
			if (!file.exists()) {
			String urlDownload = "";
			urlDownload = rootUrl+"fileName="+lstMedia.get(i).MediaUrl;
			
			DownloadManager dm = (DownloadManager)getSystemService( DOWNLOAD_SERVICE);
    		DownloadManager.Request   down=new DownloadManager.Request (Uri.parse(urlDownload));
    		down.setVisibleInDownloadsUi(true);
    		//down.setDestinationInExternalFilesDir(this, null, lstMedia.get(i).MediaUrl);
    		down.setDestinationInExternalPublicDir("Medias",lstMedia.get(i).MediaUrl);
    		//down.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
    		dm.enqueue(down);
    		
			}
		}
	}
	
	//接受下载完成后的intent  
    class DownloadCompleteReceiver extends BroadcastReceiver {  
        @Override  
        public void onReceive(Context context, Intent intent) {  
            if(intent.getAction().equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)){  
                long downId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);  
                Log.i("mch"," download complete! id : "+downId);  
                 
            }  
        }  
    }  
      
    @Override  
    protected void onResume() {  
        registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));  
        super.onResume();  
    }  
      
    @Override  
    protected void onDestroy() {  
        if(receiver != null)unregisterReceiver(receiver);  
        super.onDestroy();  
    }  
	
}
