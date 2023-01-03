import java.net.URL;

public class Request {
	public boolean containsHelp;
	public boolean containsV;
	public String method;
	public String header;
	public String data;
	public URL url;

	public Request(boolean containsHelp, boolean containsV, String method, String header, String data, URL url) {
		super();
		this.containsHelp = containsHelp;
		this.containsV = containsV;
		this.method = method;
		this.header = header;
		this.data = data;
		this.url = url;
	}

	public boolean isContainsHelp() {
		return containsHelp;
	}

	public void setContainsHelp(boolean containsHelp) {
		this.containsHelp = containsHelp;
	}

	public boolean isContainsV() {
		return containsV;
	}

	public void setContainsV(boolean containsV) {
		this.containsV = containsV;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getHeader() {
		return header;
	}

	public void setHeader(String header) {
		this.header = header;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public URL getUrl() {
		return url;
	}

	public void setUrl(URL url) {
		this.url = url;
	}
}
