/* 
*  Copyright 2012-2013 Coronastreet Networks 
*  Licensed under the Apache License, Version 2.0 (the "License"); 
*  you may not use this file except in compliance with the License. 
*  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0 
*
*  Unless required by applicable law or agreed to in writing, software 
*  distributed under the License is distributed on an "AS IS" BASIS, 
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
*  implied. See the License for the specific language governing 
*  permissions and limitations under the License 
*/
package org.coronastreet.gpxconverter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JTextArea;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.w3c.dom.Document;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@SuppressWarnings("deprecation")
public class StravaForm {

	private String loginURL = "https://www.strava.com/login";
	private String sessionURL = "https://www.strava.com/session";
	private String uploadFormURL = "http://app.strava.com/upload/select";
	private String uploadURL = "http://app.strava.com/upload/files";
	private HttpClient httpClient;  
	private HttpContext localContext;
	private CookieStore cookieStore;
	private String email;
	private String password;
	private String tripName;
	private String authToken;
	private String activityType;
	private Document tcxFile;
	private Document outDoc;
	private String rideStartTime;
	private String deviceType;
	private boolean hasAltimeter = false;
	
	private JTextArea statusTextArea;
	private List<Trkpt> trackPoints;
	
	public StravaForm() {
		
	}

	@SuppressWarnings("deprecation")
	private String convertDoc() {
        OutputFormat format = new OutputFormat(outDoc);
		format.setIndenting(true);
		StringWriter stringOut = new StringWriter ();
		XMLSerializer serializer = new XMLSerializer(stringOut, format);
	    try {
			serializer.serialize(outDoc);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    return stringOut.toString();
	}

	public boolean processData() {
	    boolean success = false;
	    
		//load the output template
		loadOutFile();
		
		setIdAndStartTime();
		setDeviceType();
		
		// Add the track data we imported to the output document
		if (addTrackData()) {
			success = true;
		}
	
		// Spit out the TCX file
		//printOutFile();
		success = true;
		return success;
	}
	
	private void setIdAndStartTime() {
		NodeList nl = outDoc.getElementsByTagName("Activity");
		
		NodeList nl2 = ((Element) nl.item(0)).getElementsByTagName("Id");
		if(nl2 != null && nl2.getLength() > 0) {
			Element el = (Element)nl2.item(0);
			el.appendChild(outDoc.createTextNode(rideStartTime));
		}
		
		NodeList nl3 = ((Element) nl.item(0)).getElementsByTagName("Lap");
		if(nl3 != null && nl3.getLength() > 0) {
			Element el = (Element)nl3.item(0);
			el.setAttribute("StartTime", rideStartTime);
		}
		
	}
	
	private Element createTrackPointElement(Trkpt tp){

		Element eTrackpoint = outDoc.createElement("Trackpoint");
		
		//create time element and time text node and attach it to the trackpoint
		Element eTime = outDoc.createElement("Time");
		eTime.appendChild(outDoc.createTextNode(tp.getTime()));
		eTrackpoint.appendChild(eTime);
		
		//create elevation element and elevation text node and attach it to the trackpoint
		Element eElevation = outDoc.createElement("AltitudeMeters");
		eElevation.appendChild(outDoc.createTextNode(tp.getElevation()));
		eTrackpoint.appendChild(eElevation);

		//create Speed Sensor element and attach it to the trackpoint
		Element eSensorState = outDoc.createElement("SensorState");
		eSensorState.appendChild(outDoc.createTextNode("Absent"));
		eTrackpoint.appendChild(eSensorState);
		
		// Create Lat/Long and add them to Position
		Element ePosition = outDoc.createElement("Position");
		Element eLatitudeDegrees = outDoc.createElement("LatitudeDegrees");
		eLatitudeDegrees.appendChild(outDoc.createTextNode(tp.getLat()));
		Element eLongitudeDegrees = outDoc.createElement("LongitudeDegrees");
		eLongitudeDegrees.appendChild(outDoc.createTextNode(tp.getLon()));
		ePosition.appendChild(eLongitudeDegrees);
		ePosition.appendChild(eLatitudeDegrees);
		eTrackpoint.appendChild(ePosition);
		
		//create HeartRate and add it to the trackpoint
		Element eHR = outDoc.createElement("HeartRateBpm");
		eHR.setAttribute("xsi:type", "HeartRateInBeatsPerMinute_t");
		Element eHRValue = outDoc.createElement("Value");
		eHRValue.appendChild(outDoc.createTextNode(tp.getHr()));
		eHR.appendChild(eHRValue);
		eTrackpoint.appendChild(eHR);
		
		//create Cadence element text node and add it to the trackpoint
		Element eCad = outDoc.createElement("Cadence");
		eCad.appendChild(outDoc.createTextNode(tp.getCad()));
		eTrackpoint.appendChild(eCad);
		
		//create Temperature element text node and add it to the trackpoint
		Element eTemp = outDoc.createElement("Temperature");
		eTemp.appendChild(outDoc.createTextNode(tp.getTemp()));
		eTrackpoint.appendChild(eTemp);
		
		return eTrackpoint;

	}

	private void setDeviceType() {
		
		// Strava only recognizes Garmin devices for Altimeter stuff
		// Everything else shows up as "Mobile"
		if (hasAltimeter) {
			deviceType = "Garmin Edge 800";
		} else {
			deviceType = "Garmin Edge 200";
		}
		
		NodeList nl = outDoc.getElementsByTagName("Activity");
		NodeList nl1 = ((Element) nl.item(0)).getElementsByTagName("Creator");
		NodeList nl2 = ((Element) nl1.item(0)).getElementsByTagName("Name");
		if(nl2 != null && nl2.getLength() > 0) {
			Element el = (Element)nl2.item(0);
			el.appendChild(outDoc.createTextNode(deviceType));
		}
	}

	private boolean addTrackData() {
		boolean success = false;
		// Get the Track element
		Element track = null;
		Element docEle = outDoc.getDocumentElement();

		// GRabbing the track node. in theory, the node list should always return 1
		NodeList nl = docEle.getElementsByTagName("Track");
		if(nl != null && nl.getLength() > 0) {
			track = (Element)nl.item(0);
		}
		
		int trkCounter = 0;
		Iterator<Trkpt> it = trackPoints.iterator();
		while(it.hasNext()) {
			Trkpt t = (Trkpt)it.next();
			Element tp = createTrackPointElement(t);
			//dumpNode(tp);
			track.appendChild(tp);
			trkCounter++;
		}
		log("Added " + trkCounter + " trackpoints to the template.");
		if (trkCounter >= 1) { success = true; }
		return success;
	}
	
	public void upload() {
		httpClient = new DefaultHttpClient();
		localContext = new BasicHttpContext();
		cookieStore = new BasicCookieStore();
		localContext.setAttribute(ClientContext.COOKIE_STORE, cookieStore);
		httpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
		
		if(doLogin()) {
			//log("Ok....logged in...");
			try {
				// Have to fetch the form to get the CSRF Token
		        HttpGet get = new HttpGet(uploadFormURL);
		        HttpResponse formResponse = httpClient.execute(get, localContext);
		        //log("Fetched the upload form...: " + formResponse.getStatusLine());
		        org.jsoup.nodes.Document doc = Jsoup.parse(EntityUtils.toString(formResponse.getEntity()));
		        String csrftoken, csrfparam;
		        Elements metalinksParam = doc.select("meta[name=csrf-param]");
		        if (!metalinksParam.isEmpty()) {
		        	csrfparam = metalinksParam.first().attr("content");
		        } else {
		        	csrfparam = null;
		        	log("Missing csrf-param?");
		        }
		        Elements metalinksToken = doc.select("meta[name=csrf-token]");
		        if (!metalinksToken.isEmpty()) {
		        	csrftoken = metalinksToken.first().attr("content");
		        } else {
		        	csrftoken = null;
		        	log("Missing csrf-token?");
		        }
		        
				HttpPost request = new HttpPost(uploadURL);
				request.setHeader("X-CSRF-Token", csrftoken);
				
				MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
				entity.addPart("method", new StringBody("post"));
				entity.addPart("new_uploader", new StringBody("1"));
				entity.addPart(csrfparam, new StringBody(csrftoken));
				entity.addPart("files[]", new InputStreamBody(document2InputStream(outDoc), "application/octet-stream", "temp.tcx"));
				
				// Need to do this bit because without it you can't disable chunked encoding, and Strava doesn't support chunked.
				ByteArrayOutputStream bArrOS = new ByteArrayOutputStream();
			    entity.writeTo(bArrOS);
			    bArrOS.flush();
			    ByteArrayEntity bArrEntity = new ByteArrayEntity(bArrOS.toByteArray());
			    bArrOS.close();

			    bArrEntity.setChunked(false);
			    bArrEntity.setContentEncoding(entity.getContentEncoding());
			    bArrEntity.setContentType(entity.getContentType());

			    request.setEntity(bArrEntity);
			    
				HttpResponse response = httpClient.execute(request, localContext);

				if (response.getStatusLine().getStatusCode() != 200) {
					log("Failed to Upload");
					HttpEntity en = response.getEntity();
					if (en != null) {
						String output = EntityUtils.toString(en);
						log(output);
					}
				} else {
					HttpEntity ent = response.getEntity();
					if (ent != null) {
						String output = EntityUtils.toString(ent);
						//log(output);
						JSONObject userInfo = new JSONArray(output).getJSONObject(0);
						//log("Object: " + userInfo.toString());
						
						if (userInfo.get("workflow").equals("Error")) {
							log("Upload Error: " + userInfo.get("error"));
						} else {
							log("Successful Uploaded. ID is " + userInfo.get("id"));
						}
					}
				}
			}catch (Exception ex) {
				log("Exception? " + ex.getMessage());
				ex.printStackTrace();
				// handle exception here
			}
		} else {
			log("Failed to upload!");
		}
	}
	
	protected InputStream document2InputStream(Document document) throws IOException {
	      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	      OutputFormat outputFormat = new OutputFormat(document);
	      XMLSerializer serializer = new XMLSerializer(outputStream, outputFormat);
	      serializer.serialize(document);
	      return new ByteArrayInputStream(outputStream.toByteArray());
	}
	
	protected boolean doLogin() {
		boolean ret = false;
		log("Authenticating athlete...");
	    try {
	        HttpGet get = new HttpGet(loginURL);
	        HttpResponse response = httpClient.execute(get, localContext);
	        //log("Fetched the login form...: " + response.getStatusLine());
	        org.jsoup.nodes.Document doc = Jsoup.parse(EntityUtils.toString(response.getEntity()));
	        String csrftoken, csrfparam;
	        
	        Elements metalinksParam = doc.select("meta[name=csrf-param]");
	        if (!metalinksParam.isEmpty()) {
	        	csrfparam = metalinksParam.first().attr("content");
	        	//log("Setting csrf-param to " + csrfparam);
	        } else {
	        	csrfparam = null;
	        	log("Missing csrf-param?");
	        }
	        
	        Elements metalinksToken = doc.select("meta[name=csrf-token]");
	        if (!metalinksToken.isEmpty()) {
	        	csrftoken = metalinksToken.first().attr("content");
	        	//log("Setting csrf-token to " + csrftoken);
	        } else {
	        	csrftoken = null;
	        	log("Missing csrf-token?");
	        }
	        
	        HttpPost post = new HttpPost(sessionURL);
	        post.setHeader("Referer", "https://www.strava.com/login");
	        List <NameValuePair> nvps = new ArrayList <NameValuePair>();
	        nvps.add(new BasicNameValuePair(csrfparam, csrftoken));
	        nvps.add(new BasicNameValuePair("plan", ""));
	        nvps.add(new BasicNameValuePair("email", email));
	        nvps.add(new BasicNameValuePair("password", password));

	        post.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
	        
	        HttpResponse sessionResponse = httpClient.execute(post, localContext);

	        if (sessionResponse.getStatusLine().getStatusCode() != 302) {
	        	log("Failed to Login. " + sessionResponse.getStatusLine().getStatusCode());
	        	String output = EntityUtils.toString(sessionResponse.getEntity());
	        	log(output);
	        	ret = false;
			} else {
				ret = true;
			}
	        HttpEntity entity = sessionResponse.getEntity();
            EntityUtils.consume(entity);
			
	    }catch (Exception ex) {
	        // handle exception here
	    	ex.printStackTrace();
	    }
		return ret;
	}
		
	private void loadOutFile(){
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder db = dbf.newDocumentBuilder();
			log("Loading TCX template file.");			
			outDoc = db.parse(this.getClass().getResourceAsStream("/org/coronastreet/gpxconverter/tcxtemplate.xml"));
		}catch(ParserConfigurationException pce) {
			pce.printStackTrace();
		}catch(SAXException se) {
			se.printStackTrace();
		}catch(IOException ioe) {
			ioe.printStackTrace();
		}		
	}

	private void dumpNode(JSONObject o) {
		log(o.toString(2));
	}
	
	private void log(String s) {
		this.statusTextArea.append("STRAVA: " + s + "\n");
		this.statusTextArea.repaint(1);
	}
	
	private void log(InputStream is) {
		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is));
			String line = "";
			while ((line = rd.readLine()) != null) {
				log(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	public List<Trkpt> getTrackPoints() {
		return trackPoints;
	}
	public void setTrackPoints(List<Trkpt> trackPoints) {
		this.trackPoints = trackPoints;
	}
	public JTextArea getStatusTextArea() {
		return statusTextArea;
	}
	public void setStatusTextArea(JTextArea statusTextArea) {
		this.statusTextArea = statusTextArea;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public String getTripName() {
		return tripName;
	}
	public void setTripName(String tripName) {
		this.tripName = tripName;
	}
	public String getActivityType() {
		return activityType;
	}

	public void setActivityType(String at) {
		this.activityType = at;
	}
	public String getRideStartTime() {
		return rideStartTime;
	}

	public void setRideStartTime(String rideStartTime) {
		this.rideStartTime = rideStartTime;
	}
	
	public void setHasAltimeter(boolean hasAltimeter) {
		this.hasAltimeter = hasAltimeter;
	}
	
	
}
