/**
 * Copyright ©2023-2025 LogonBox Ltd
 * All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.logonbox.vpn.client.common;

import java.io.Serializable;
import java.net.HttpCookie;
import java.util.Objects;

import uk.co.bithatch.nativeimage.annotations.Serialization;

@Serialization
public class CookieWrapper implements Serializable {
	private static final long serialVersionUID = 1L;

	private String comment;
	private int version;
	private String value;
	private boolean secure;
	private String path;
	private String name;
	private long maxAge;
	private String portlist;
	private String domain;
	private boolean discard;
	private String commentUrl;

	public CookieWrapper() {
	}

	public CookieWrapper(HttpCookie cookie) {
		comment = cookie.getComment();
		commentUrl = cookie.getCommentURL();
		discard = cookie.getDiscard();
		domain = cookie.getDomain();
		maxAge = cookie.getMaxAge();
		name = cookie.getName();
		path = cookie.getPath();
		portlist = cookie.getPortlist();
		secure = cookie.getSecure();
		value = cookie.getValue();
		version = cookie.getVersion();
	}

	@Override
	public int hashCode() {
		return Objects.hash(name);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CookieWrapper other = (CookieWrapper) obj;
		return Objects.equals(name, other.name);
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public boolean isSecure() {
		return secure;
	}

	public void setSecure(boolean secure) {
		this.secure = secure;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getMaxAge() {
		return maxAge;
	}

	public void setMaxAge(long maxAge) {
		this.maxAge = maxAge;
	}

	public String getPortlist() {
		return portlist;
	}

	public void setPortlist(String portlist) {
		this.portlist = portlist;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public boolean isDiscard() {
		return discard;
	}

	public void setDiscard(boolean discard) {
		this.discard = discard;
	}

	public String getCommentUrl() {
		return commentUrl;
	}

	public void setCommentUrl(String commentUrl) {
		this.commentUrl = commentUrl;
	}

	public HttpCookie toHttpCookie() {
		HttpCookie c = new HttpCookie(name, value);
		c.setComment(comment);
		c.setCommentURL(commentUrl);
		c.setDiscard(discard);
		c.setDomain(domain);
		c.setMaxAge(maxAge);
		c.setPath(path);
		c.setPortlist(portlist);
		c.setSecure(secure);
		c.setVersion(version);

		return c;
	}

	@Override
	public String toString() {
		return "CookieWrapper [comment=" + comment + ", version=" + version + ", value=" + value + ", secure="
				+ secure + ", path=" + path + ", name=" + name + ", maxAge=" + maxAge + ", portlist=" + portlist
				+ ", domain=" + domain + ", discard=" + discard + ", commentUrl=" + commentUrl + "]";
	}
}