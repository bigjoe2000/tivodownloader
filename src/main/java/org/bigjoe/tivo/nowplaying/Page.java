package org.bigjoe.tivo.nowplaying;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class Page {
	
	public int start = 0;
	public int count = 0;
	public int total = 0;
	public List<Item> items = new ArrayList<>();

	public static Page parse(Document doc) {
		Page page = new Page();
		page.start = Integer.parseInt(doc.select("ItemStart").first().text());
		page.count = Integer.parseInt(doc.select("ItemCount").first().text());
		page.total = Integer.parseInt(doc.select("TotalItems").first().text());

	    for (Element e : doc.select("Item")) {
			page.items.add(Item.parse(e));
        }

		return page;
	}
}
