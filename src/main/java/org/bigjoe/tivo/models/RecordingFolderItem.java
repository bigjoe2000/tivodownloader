package org.bigjoe.tivo.models;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

public class RecordingFolderItem extends TivoObject {
	public String bodyId;
	public String childRecordingId;
	public String recordingFolderItem;
	public String startTime;
	public int newItems;
	public String title;
	public String recordingStatusType;
	public String collectionId;
	public String collectionType;
	public String transportType;
	public String contentId;
	public List<SubscriptionIdentifier> subscriptionIdentifier;
	public boolean isAdult;
	public boolean recordingStreamProhibited;
	public boolean recordingTransferProhibited;
	public RecordingList recordingList;
	public double percentWatched;

	public String recordingId;
	public String mfsId;

	public String getDownloadUrl(String tivoIp) throws UnsupportedEncodingException {
		return "http://" + tivoIp + ":80/download/" 
			// Although we use title here, it could very well be any random string!!!
			+ "xxx"
			// + URLEncoder.encode(title, "UTF-8") 
			+ ".TiVo?Container=%2FNowPlaying&id="
			+ mfsId;
	}

	/**
	 * Return a string we can use to uniquely identify the recording
	 * 
	 * Recording id looks like: tivo:rc.4481929
	 * @return
	 */
	public String getIdentifier() {
		return recordingId.split("\\.")[1];
	}

/** 
{
	"bodyId":"tsn:84600019078B9D8",
	"childRecordingId":"tivo:rc.16426009",
	"recordingFolderItemId":"tivo:rf.16426009",
	"startTime":"2025-02-01 15:00:00",
	"title":"NHL Alumni Game: \"New York Islanders vs. New York Rangers\"",
	"newItems":1,
	"recordingStatusType":"inProgressRecording",
	"collectionId":"tivo:cl.512543166",
	"collectionType":"series",
	"transportType":"stream",
	"contentId":"tivo:ct.512543165",
	"subscriptionIdentifier":[{
		"subscriptionId":"tivo:sb.2090259",
		"type":"subscriptionIdentifier",
		"subscriptionType":"seasonPass"
	}],
	"isAdult":false,
	"recordingStreamProhibited":false,
	"recordingTransferProhibited":false,
	"percentWatched":0,
	"type":"recordingFolderItem",
	"details":{
		"isBottom":false,
		"isTop":true,
		"recording":[{
			"actualEndTime":"2025-02-01 17:07:56",
			"actualStartTime":"2025-02-01 14:59:58",
			"bodyId":"tsn:84600019078B9D8",
			"category":[
				{"categoryId":"tivo:ca.413827","label":"Hockey","topLevel":false,"type":"category"},
				{"categoryId":"tivo:ca.10916239","label":"Sports Event","topLevel":false,"type":"category"},
				{"categoryId":"tivo:ca.413905","label":"Sports","topLevel":true,"type":"category"}
			],
			"cc":true,
			"channel":{
				"bodyId":"tsn:84600019078B9D8",
				"callSign":"MSGHD",
				"channelId":"tivo:ch.9",
				"channelNumber":"578",
				"isDiscovered":false,
				"isFavorite":false,
				"isHidden":false,
				"isKidZone":false,
				"isReceived":true,
				"levelOfDetail":"high",
				"name":"MSGHD",
				"sourceType":"cable",
				"stationId":"tivo:st.18826389",
				"isDigital":false,
				"logoIndex":65609,
				"isBlocked":false,
				"isHdtv":true,
				"isEntitled":true,
				"type":"channel"
			},
			"collectionId":"tivo:cl.512543166",
			"collectionType":"series",
			"colorType":"color",
			"contentId":"tivo:ct.512543165",
			"contentType":"video",
			"deletionPolicy":"neverDelete",
			"description":"Greats from the New York Islanders and New York Rangers skate against each other at Madison Square Garden. The Alumni Classic benefits the Garden of Dreams Foundation.",
			"diskPartition":"user",
			"drm":{
				"cgms":"copyFreely",
				"multiRoomView":true,"
				tivoToGo":true,
				"multiRoomStream":true,
				"mrsPlaybackPolicy":"allowed",
				"type":"drm",
				"recordingPlaybackPolicy":"allowed"
			},
			"duration":7678,
			"episodic":true,
			"expectedDeletion":"2038-01-19 03:14:00",
			"hdtv":true,
			"isAdult":false,
			"isEpisode":true,
			"levelOfDetail":"high",
			"mimeType":"video/mpg2",
			"offerId":"tivo:of.ctd.18826389.578.cable.2025-02-01-15-00-00.7200",
			"originalAirdate":"2025-01-11",
			"partnerCollectionId":"EP0512543166-0512543165",
			"recordingId":"tivo:rc.16426009",
			"requestedEndPadding":1800,
			"requestedEndTime":"2025-02-01 17:00:00",
			"requestedStartPadding":0,
			"requestedStartTime":"2025-02-01 15:00:00",
			"scheduledEndTime":"2025-02-01 17:30:00",
			"scheduledStartTime":"2025-02-01 15:00:00",
			"size":1013760,
			"startTime":"2025-02-01 15:00:00",
			"state":"inProgress",
			"subscriptionForCollectionIdAndChannel":[{
				"bodyId":"",
				"useOfferEndPadding":false,
				"useOfferStartPadding":false,
				"type":"subscription"}],
			"subscriptionIdentifier":[{
				"subscriptionId":"tivo:sb.2090259",
				"type":"subscriptionIdentifier",
				"subscriptionType":"seasonPass"
			}],
			"subtitle":"New York Islanders vs. New York Rangers",
			"suggestionScore":0,
			"title":"NHL Alumni Game",
			"transportType":"stream",
			"watchedTime":0,
			"quality":"best",
			"descriptionLanguage":"English",
			"desiredDeletion":"2038-01-19 03:14:00",
			"collectionTitle":"New York Rangers (Hockey)",
			"remindUser":false,
			"isNew":true,
			"sportsEventInfo":{
				"awayTeam":{
					"name":"New York Islanders",
					"teamId":"tivo:te.365219145",
					"type":"team",
					"sportName":"Hockey"
				},
				"homeTeam":{
					"name":"New York Rangers",
					"teamId":"tivo:te.365219146",
					"type":"team",
					"sportName":"Hockey"
				},
				"league":{
					"leagueId":"tivo:lg.365217539",
					"name":"National Hockey League",
					"type":"league",
					"sportName":" "
				},
				"type":"teamSportsEventInfo",
				"sportEventType":"game"
			},
			"type":"recording",
			"videoResolution":"hd"
		}],
		"type":"recordingList",
		"IsFinal":true
	}
}	

*/


}
