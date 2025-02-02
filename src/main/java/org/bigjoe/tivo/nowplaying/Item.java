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


