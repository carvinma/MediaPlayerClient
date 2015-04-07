package com.mediaplayclient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;

import android.view.View;

import android.view.KeyEvent;

import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;


public class MainActivity extends Activity {
	private String serverUrl="http://123.57.39.141/test/android/GetServerData?";
	private String rootUrl="http://123.57.39.141/test/android/DownloadMedia?";
	String downloadDir="/mnt/sdcard/medias/";
	
	public static final String LOG_TAG = "test";  
    
    private ProgressDialog mProgressDialog;  
    public static final int DIALOG_DOWNLOAD_PROGRESS = 0;    
    File rootDir = Environment.getExternalStorageDirectory();  
	
    
	
	//播放器
	private VideoView myVideoView; // 声明一个变量和页面的元素对应起来			
	private TextView mTime; // 显示时间的
	private TextView tvSubtitle;//显示字幕
	private ImageView apkIcon;//显示图标
    private String sn;
	private SQLiteDatabase db1;
	private DBHelper dbhelper;

	List<MediaInfo> lstServerMedia = new ArrayList<MediaInfo>();// 存放服务器端的影片信息
	List<MediaInfo> lstPlayMedia = new ArrayList<MediaInfo>();// 存放可以播放的影片信息
	List<MediaInfo> lstDownloadMedia = new ArrayList<MediaInfo>();// 存放需要下载的影片信息
	private int mediaindex = 0;
	
	private int internalSendSn=10000;//10s
	private int internalRequestMedia=100000;//100s
	
	//定义要下载的文件名  
    public String fileName = "";  
    public String fileURL = "";
	public int downCount=0;
	private static final int msgKey1 = 1;//获取本设备的时间信息
	private static final int msgKey2 = 2;//获取服务器的时间信息
	private static final int msgKey3 = 3;//程序启动后，首次发送json信息到服务器
	private static final int msgKey4 = 4;//程序间隔100s，请求影片列表
	private static final int msgKey5 = 5;//下载影片
	
	//boolean IsMediaUpdate=false;
	
	
	Context mContext;  
    //DownloadManager manager ;  
    //DownloadCompleteReceiver receiver;  
	
	private Handler handler = new Handler() {
		@Override
		// 当有消息发送出来的时候就执行Handler的这个方法
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {			
			case msgKey1:			
				long sysTime = System.currentTimeMillis();
				CharSequence sysTimeStr = DateFormat.format("hh:mm:ss", sysTime);
				mTime.setText(sysTimeStr);
				handler.sendEmptyMessageDelayed(msgKey1, 1000);
				break;
			case msgKey2:
				Log.i("mch", "get server time");
				break;
			case msgKey3:
				SendEqInfo();		
				handler.sendEmptyMessageDelayed(msgKey3, internalSendSn);
				break;
			case msgKey4:
				Log.i("mch", "Request media list");
				GetServerMediaList();
				GetLocalMedia();
				downLoadMedias();
				if(myVideoView.isPlaying())
					myVideoView.stopPlayback();
				PlayMedias();

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
		
		//检查下载目录是否存在   
        checkAndCreateDirectory("/mydownloads"); 
		
		
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
			//GetMyMediaList();
		}
		catch(Exception e)
		{
			
		}
		
				
		//第三循环播放视频列表
		//lstMedia=GetLocalMedia();
		try{
		//downLoadMovies();
		}
		catch(Exception ex)
		{
			Log.i("download address", "ex3 download list");
		}
		//PlayMovies();
        
		// String s1=getLocalIpAddress();
		// String s2 = getLocalMacAddress();
	}

	// 播放视频列表
	public void PlayMedias() {		
		if(lstPlayMedia.size()>0)
		{
			String videoPath=downloadDir+lstPlayMedia.get(mediaindex).MediaUrl;
			Log.i("download address", videoPath+"_"+mediaindex);
			File file = new File(videoPath);
			if (!file.exists() ||!file.renameTo(file) ||file.length()==0) {				
				mediaindex=mediaindex+1;
			return;
			}
		myVideoView.setVideoPath(file.getAbsolutePath());		
		}
		else
		{
			downLoadMedias();
		}
		myVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
			@Override
			public void onPrepared(MediaPlayer mp) {
				setProgressBarVisibility(false);
				mp.start();	
				tvSubtitle.setText(lstPlayMedia.get(mediaindex).Description);
				tvSubtitle.requestFocus();
				mediaindex = mediaindex + 1;
			}
		});

		myVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
					@Override
					public void onCompletion(MediaPlayer mp) {
						handler.sendEmptyMessageAtTime(msgKey4, 1000);
						if (lstPlayMedia.size() > mediaindex) {					
							String videoPath=downloadDir+lstPlayMedia.get(mediaindex).MediaUrl;
							Log.i("download address", videoPath);
							File file = new File(videoPath);
							if (!file.exists() ||!file.renameTo(file)||file.length()==0) {
								mediaindex=mediaindex+1;
								return;
							}
							myVideoView.setVideoPath(file.getAbsolutePath());
						} else {
							mediaindex = 0;
							File file = new File(downloadDir+lstPlayMedia.get(mediaindex).MediaUrl);
							if (!file.exists()||!file.renameTo(file)||file.length()==0) {
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
	public void GetServerMediaList() {
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
			MediaInfo model=new MediaInfo();
			model.MediaName =  item.getString("MediaName");
			model.MediaUrl = item.getString("MediaUrl");
			model.Description = item.getString("SubTitle");
			 Log.i("mch json", model.MediaName);
			lst.add(model);			
		}
		if(lst.size()>0)
		{
			SaveData(lst);
		}
		//if(lstServerMedia.size()==0)
		//	lstServerMedia=GetLocalMedia();
		//if(IsMediaListUpdate(lstMedia,lst)){
		//	SaveData(lst);
		//	IsMediaUpdate=true;
		//}
		
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

	// 从数据库里查询自己能播放的视频，进行播放
	private void GetLocalMedia() {		
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
				String videoPath=downloadDir+model.MediaUrl;
				File file = new File(videoPath);
				if (file.exists() &&file.length()>0) {
					lstPlayMedia.add(model);
				}
				else
				{
					lstDownloadMedia.add(model);
				}
				
				Log.i("mch local data", model.MediaName);
			}		
			c.close();
			db1.close();
		}
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
	
	private void downLoadMedias() {		
		int count=lstDownloadMedia.size();
		for(int i=0;i<count;i++)
		{			
			fileName=lstDownloadMedia.get(i).MediaUrl;
			this.fileURL= rootUrl+"fileName="+fileName;
			new DownloadFileAsync().execute(fileURL);  
			//DownloadManager dm = (DownloadManager)getSystemService( DOWNLOAD_SERVICE);
    		//DownloadManager.Request   down=new DownloadManager.Request (Uri.parse(urlDownload));
    		//down.setVisibleInDownloadsUi(true);
    		////down.setDestinationInExternalFilesDir(this, null, lstMedia.get(i).MediaUrl);
    		//down.setDestinationInExternalPublicDir("Medias",lstMedia.get(i).MediaUrl);
    		////down.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
    		//dm.enqueue(down);
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
      
    //@Override  
    //protected void onResume() {  
    //    registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));  
    //    super.onResume();  
    //}  
      
    //@Override  
    //protected void onDestroy() {  
    //    if(receiver != null)unregisterReceiver(receiver);  
    //    super.onDestroy();  
    //}  
    
   
    class DownloadFileAsync extends AsyncTask<String, String, String> {  
        
        @Override  
        protected void onPreExecute() {  
            super.onPreExecute();  
            showDialog(DIALOG_DOWNLOAD_PROGRESS);  
        }  
  
          
        @Override  
        protected String doInBackground(String... aurl) {  
  
            try {  
                //连接地址  
                URL u = new URL(fileURL);  
                HttpURLConnection c = (HttpURLConnection) u.openConnection();  
                c.setRequestMethod("GET");  
                c.setDoOutput(true);  
                c.connect();  
                  
                //计算文件长度  
                int lenghtOfFile = c.getContentLength();  
                String filePath=rootDir + "/my_downloads/"+fileName;
                File fi=new File(rootDir + "/my_downloads/", fileName);
                if(fi.exists()&&fi.renameTo(fi) && fi.length()==0)
                	return null;
                FileOutputStream f = new FileOutputStream(fi);  
            
                InputStream in = c.getInputStream();  
  
               //下载的代码  
                byte[] buffer = new byte[1024];  
                int len1 = 0;  
                long total = 0;  
                  
                while ((len1 = in.read(buffer)) > 0) {  
                    //total += len1; //total = total + len1  
                    //publishProgress("" + (int)((total*100)/lenghtOfFile));  
                    f.write(buffer, 0, len1);  
                }  
                f.close();  
                downCount++; 
                publishProgress("" + (int)((downCount*100)/lstDownloadMedia.size())); 
                
               copyFile(filePath, downloadDir+"/"+fileName);
                
                
                
            } catch (Exception e) {  
                Log.d(LOG_TAG, e.getMessage());  
            }  
              
            return null;  
        }  
          
        protected void onProgressUpdate(String... progress) {  
             Log.d(LOG_TAG,progress[0]);  
             mProgressDialog.setProgress(Integer.parseInt(progress[0]));  
        }  
  
        @Override  
        protected void onPostExecute(String unused) {  
            //dismiss the dialog after the file was downloaded         	
            dismissDialog(DIALOG_DOWNLOAD_PROGRESS);  
        } 
        
    }  
      
    public void copyFile(String oldPath, String newPath) {   
        try {   
            int bytesum = 0;   
            int byteread = 0;   
            File oldfile = new File(oldPath);   
            if (oldfile.exists()) { //文件存在时   
                InputStream inStream = new FileInputStream(oldPath); //读入原文件   
                FileOutputStream fs = new FileOutputStream(newPath);   
                byte[] buffer = new byte[1444];   
                int length;   
                while ( (byteread = inStream.read(buffer)) != -1) {   
                    bytesum += byteread; //字节数 文件大小   
                    System.out.println(bytesum);   
                    fs.write(buffer, 0, byteread);   
                }   
                inStream.close();   
            }   
        }   
        catch (Exception e) {   
            System.out.println("复制单个文件操作出错");   
            e.printStackTrace();   
   
        }   
   
    }   
      
    public void checkAndCreateDirectory(String dirName){  
        File new_dir = new File( rootDir + dirName );  
        if( !new_dir.exists() ){  
            new_dir.mkdirs();  
        }  
    }  

      
        @Override  
    protected Dialog onCreateDialog(int id) {  
        switch (id) {  
            case DIALOG_DOWNLOAD_PROGRESS: //we set this to 0  
                mProgressDialog = new ProgressDialog(this);  
                mProgressDialog.setMessage("Downloading file...");  
                mProgressDialog.setIndeterminate(false);  
                mProgressDialog.setMax(100);  
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);  
                mProgressDialog.setCancelable(true);  
                mProgressDialog.show();  
                return mProgressDialog;  
            default:  
                return null;  
        }  
    }  

    
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		switch (event.getAction()) {
		case KeyEvent.ACTION_DOWN:
			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_MENU:
				if (event.getRepeatCount() == 0) {
					Toast.makeText(MainActivity.this, "长按菜单键可以进入设置模式", Toast.LENGTH_SHORT).show();
				} else {
					Intent intent = new Intent(Settings.ACTION_SETTINGS);
					startActivity(intent);
				}
				break;

			default:
				break;
			}

			break;

		default:
			break;
		}

		return super.dispatchKeyEvent(event);
	}

	
}
