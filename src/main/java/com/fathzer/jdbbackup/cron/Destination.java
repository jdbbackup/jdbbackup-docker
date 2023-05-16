package com.fathzer.jdbbackup.cron;

import static com.fathzer.jdbbackup.DestinationManager.URI_PATH_SEPARATOR;

/** This a copy of the jdbbackup-core non public com.fathzer.jdbbackup.Destination class.<br>
 * //TODO Maybe it should be better to prevent this copy by maling it public in jdbbackup-core
 */
class Destination {
	private String type;
	private String path;
	
	/** Constructor.
	 * @param dest The string representation of the destination. Should have the format <i>scheme</i>://[<i>path</i>] where <i>scheme</i> should not contain any colon.
	 */
	Destination(String dest) {
		if (dest==null) {
			throw new IllegalArgumentException();
		}
		int index = dest.indexOf(':');
		if (index<=0) {
			throw new IllegalArgumentException ("Destination scheme is missing in "+dest);
		}
		this.type = dest.substring(0, index);
		for (int i=1;i<=2;i++) {
			if (index+i>=dest.length() || dest.charAt(index+i)!=URI_PATH_SEPARATOR) {
				throw new IllegalArgumentException("Destination has not the right format: "+dest+" does not not match scheme://path");
			}
		}
		this.path = dest.substring(index+3);
	}

	public String getScheme() {
		return type;
	}

	public String getPath() {
		return path;
	}
}
