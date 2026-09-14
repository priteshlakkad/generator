package com.quoteweave.platform.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "quoteweave.render")
public class PdfRenderProperties {

	/**
	 * Base URI that relative resource paths in a template resolve against, e.g.
	 * {@code https://portal.example.com/}. Leave blank to require absolute URLs.
	 */
	private String baseUri = "";

	/** Optional product images named by catalogue code; checked before bundled assets and URLs. */
	private String imagesDir = "./image-store";

	private int connectTimeoutMs = 3000;

	private int readTimeoutMs = 5000;

	/** Resources larger than this are abandoned rather than pulled into memory. */
	private long maxResourceBytes = 5 * 1024 * 1024L;

	/** Number of fetched resources kept in memory; the main win is repeated images across many rows. */
	private int cacheSize = 256;

	/**
	 * Hosts allowed to serve remote resources. Empty means "allow any host", which keeps existing
	 * setups working; set it to your portal's host to stop payload-supplied URLs reaching arbitrary
	 * internal addresses.
	 */
	private List<String> allowedHosts = new ArrayList<>();

	public String getImagesDir() { return imagesDir; }

	public void setImagesDir(String imagesDir) { this.imagesDir = imagesDir; }

	public String getBaseUri() {
		return baseUri;
	}

	public void setBaseUri(String baseUri) {
		this.baseUri = baseUri;
	}

	public int getConnectTimeoutMs() {
		return connectTimeoutMs;
	}

	public void setConnectTimeoutMs(int connectTimeoutMs) {
		this.connectTimeoutMs = connectTimeoutMs;
	}

	public int getReadTimeoutMs() {
		return readTimeoutMs;
	}

	public void setReadTimeoutMs(int readTimeoutMs) {
		this.readTimeoutMs = readTimeoutMs;
	}

	public long getMaxResourceBytes() {
		return maxResourceBytes;
	}

	public void setMaxResourceBytes(long maxResourceBytes) {
		this.maxResourceBytes = maxResourceBytes;
	}

	public int getCacheSize() {
		return cacheSize;
	}

	public void setCacheSize(int cacheSize) {
		this.cacheSize = cacheSize;
	}

	public List<String> getAllowedHosts() {
		return allowedHosts;
	}

	public void setAllowedHosts(List<String> allowedHosts) {
		this.allowedHosts = allowedHosts;
	}
}
