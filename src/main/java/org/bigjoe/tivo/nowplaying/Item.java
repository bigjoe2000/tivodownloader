package org.bigjoe.tivo.nowplaying;

import org.jsoup.nodes.Element;

public class Item {
	
	public String contentLink;
	public String detailLink;
	public String title;
	public String episodeTitle;
	public String programId;
	public boolean canStream;
	public boolean available = true;
	public String raw;

	public static Item parse(Element e) {
		Item item = new Item();

		Element links = e.select("Links").first();
		item.contentLink = links.selectFirst("Content").selectFirst("Url").text();
		item.available = links.selectFirst("Content").select("available").isEmpty();
		item.detailLink = links.selectFirst("TivoVideoDetails").selectFirst("Url").text();

		item.title = e.select("Title").text();
		item.episodeTitle = e.select("EpisodeTitle").text();
		item.programId = e.select("ProgramId").text();
		item.canStream = "Yes".equals(e.select("StreamingPermission").text());
		item.raw = e.outerHtml();
		return item;
	}
}


/**
 * 	<Item>
 * 		<Details>
			<ContentType>video/x-tivo-raw-tts</ContentType>
			<SourceFormat>video/x-tivo-raw-tts</SourceFormat>
			<Title>Storage Wars</Title>
			<SourceSize>3114270720</SourceSize>
			<Duration>1800000</Duration>
			<CaptureDate>0x676ECEFF</CaptureDate>
			<ShowingDuration>1800000</ShowingDuration>
			<StartPadding>0</StartPadding>
			<EndPadding>0</EndPadding>
			<ShowingStartTime>0x676ECF00</ShowingStartTime>
			<EpisodeTitle>Quality Bro Chime</EpisodeTitle>
			<Description>It's &quot;Bring Your Brother to Work Day&quot; in Thousand Oaks. Justin sinks his money into a unit with an interesting pair; Dave pushes Mary to the breaking point; and Kenny finds the time to make a nice profit piece by piece. Copyright Rovi, Inc.</Description>
			<SourceChannel>681</SourceChannel>
			<SourceStation>A&amp;EHD-E</SourceStation>
			<HighDefinition>Yes</HighDefinition>
			<ProgramId>EP0171836104-0386034088</ProgramId>
			<SeriesId>SH0171836104</SeriesId>
			<StreamingPermission>Yes</StreamingPermission>
			<ProgramServerId>386034088</ProgramServerId>
			<SeriesServerId>171836104</SeriesServerId>
			<TvRating>4</TvRating>
			<ShowingBits>4643</ShowingBits>
			<SourceType>2</SourceType>
			<IdGuideSource>75763224</IdGuideSource>
		</Details>
		<Links>
			<Content>
				<Url>http://10.20.7.40:80/download/Storage%20Wars.TiVo?Container=%2FNowPlaying&amp;id=600006</Url>
				<ContentType>video/x-tivo-raw-tts</ContentType>
			</Content>
			<CustomIcon>
				<Url>urn:tivo:image:expired-recording</Url>
				<ContentType>image/*</ContentType>
				<AcceptsParams>No</AcceptsParams>
			</CustomIcon>
			<TiVoVideoDetails>
				<Url>https://10.20.7.40:443/TiVoVideoDetails?id=600006</Url>
				<ContentType>text/xml</ContentType>
				<AcceptsParams>No</AcceptsParams>
			</TiVoVideoDetails>
		</Links>
	</Item>

 */