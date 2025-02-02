package org.bigjoe.tivo.models;

import java.util.List;

public class RecordingList extends TivoObject {

	public boolean isBottom;
	public boolean isTop;
	public List<Recording> recording;
	boolean isFinal;
	
}
