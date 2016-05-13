package com.vg.live.player;

import org.stjs.javascript.Array;
import org.stjs.javascript.JSGlobal;
import org.stjs.javascript.Map;
import org.stjs.javascript.RegExp;

import static org.stjs.javascript.Global.console;
import static org.stjs.javascript.JSCollections.$array;
import static org.stjs.javascript.JSCollections.$castArray;
import static org.stjs.javascript.JSCollections.$map;
import static org.stjs.javascript.JSObjectAdapter.hasOwnProperty;

public class HLSPlaylist {
    public static final String NO = "NO";
    public static final String YES = "YES";
    private static final String ENDLIST = "#EXT-X-ENDLIST";
    private static final String EXTM3U = "#EXTM3U";
    private static final String EXTINF = "#EXTINF";
    private static final String ALLOW_CACHE = "#EXT-X-ALLOW-CACHE";
    private static final String START = "#EXT-X-START";
    private static final String MEDIA_SEQUENCE = "#EXT-X-MEDIA-SEQUENCE";
    /**
     * The EXT-X-TARGETDURATION tag specifies the maximum media segment
     * duration. The EXTINF duration of each media segment in the Playlist file
     * MUST be less than or equal to the target duration.
     */
    private static final String TARGETDURATION = "#EXT-X-TARGETDURATION";
    /**
     * Furthermore, the Playlist file MAY contain an EXT-X-PLAYLIST-TYPE tag
     * with a value of either EVENT or VOD. If the tag is present and has a
     * value of EVENT, the server MUST NOT change or delete any part of the
     * Playlist file (although it MAY append lines to it). If the tag is present
     * and has a value of VOD, the Playlist file MUST NOT change.
     */
    private static final String PLAYLIST_TYPE = "#EXT-X-PLAYLIST-TYPE";
    private static final String VERSION = "#EXT-X-VERSION";
    /**
     * BANDWIDTH
     *
     * The value is a decimal-integer of bits per second. It MUST be an upper
     * bound of the overall bitrate of each media segment (calculated to include
     * container overhead) that appears or will appear in the Playlist.
     *
     * Every EXT-X-STREAM-INF tag MUST include the BANDWIDTH attribute.
     *
     */
    public static final String BANDWIDTH = "BANDWIDTH";
    /**
     * RESOLUTION
     *
     * The value is a decimal-resolution describing the approximate encoded
     * horizontal and vertical resolution of video within the presentation. The
     * RESOLUTION attribute is OPTIONAL but is recommended if the variant stream
     * includes video.
     */
    public static final String RESOLUTION = "RESOLUTION";
    public static final String URI = "URI";
    public static final String AUTOSELECT = "AUTOSELECT";
    public static final String DEFAULT = "DEFAULT";
    /**
     * GROUP-ID
     *
     * The value is a quoted-string identifying a mutually-exclusive group of
     * renditions. The presence of this attribute signals membership in the
     * group. See Section 3.4.9.1. This attribute is REQUIRED.
     */
    public static final String GROUP_ID = "GROUP-ID";

    /**
     * TYPE
     *
     * The value is enumerated-string; valid strings are AUDIO, VIDEO, SUBTITLES
     * and CLOSED-CAPTIONS. If the value is AUDIO, the Playlist described by the
     * tag MUST contain audio media. If the value is VIDEO, the Playlist MUST
     * contain video media. If the value is SUBTITLES, the Playlist MUST contain
     * subtitle media. If the value is CLOSED-CAPTIONS, the media segments for
     * the video renditions can include closed captions. This attribute is
     * REQUIRED.
     *
     */
    public static final String TYPE = "TYPE";
    public static final String VIDEO = "VIDEO";
    public static final String AUDIO = "AUDIO";

    /**
     *
     * NAME
     *
     * The value is a quoted-string containing a human-readable description of
     * the rendition. If the LANGUAGE attribute is present then this description
     * SHOULD be in that language. This attribute is REQUIRED.
     */
    public static final String NAME = "NAME";

    /**
     * The EXT-X-MEDIA tag is used to relate Media Playlists that contain
     * alternative renditions of the same content. For example, three
     * EXT-X-MEDIA tags can be used to identify audio-only Media Playlists that
     * contain English, French and Spanish renditions of the same presentation.
     * Or two EXT-X-MEDIA tags can be used to identify video- only Media
     * Playlists that show two different camera angles.
     */
    private static final String Related = "#EXT-X-MEDIA";

    /**
     * The EXT-X-STREAM-INF tag specifies a variant stream, which is a set of
     * renditions which can be combined to play the presentation. The attributes
     * of the tag provide information about the variant stream.
     */
    private static final String _Variant = "#EXT-X-STREAM-INF";

    public static final String EVENT = "EVENT";
    public static final String VOD = "VOD";

    public static class RelatedPlaylist {
        public RelatedPlaylist(Map<String, String> attrs) {
            this.attributes = attrs;
        }

        public Map<String, String> attributes;
    }

    public static Variant createVariant(String uri, Map<String, String> attributes) {
        Variant v = new Variant();
        v.uri = uri;
        v.attributes = attributes;
        return v;
    }

    public static class Variant {
        public String uri;
        public Map<String, String> attributes;
    }

    public static class Media {
        public Media(String url, double durationSec) {
            this.url = url;
            this.durationSec = durationSec;
        }

        public final String url;
        public final double durationSec;
    }

    public String uri;
    Map<String, Array<String>> headers;
    Array<Media> mediaList;
    Array<Variant> variantList;
    boolean endList;

    public HLSPlaylist() {
        this.headers = $map();
        this.mediaList = $array();
        this.variantList = $array();
    }

    public Array<RelatedPlaylist> getRelatedPlaylists() {
        Array<String> list = headers.$get(Related);
        Array<RelatedPlaylist> collect = list.map((str, i, a) -> new RelatedPlaylist(parseAttributeList(str)));
        return collect;
    }

    public Array<Variant> getVariantList() {
        return copyOf(variantList);
    }

    private <T> Array<T> copyOf(Array<T> list) {
        return list.slice(0);
    }

    public double getDurationSec() {
        double sum = 0;
        for (int i = 0; i < mediaList.$length(); i++) {
            sum += mediaList.$get(i).durationSec;
        }
        return sum;
    }

    public Array<Media> getMediaList() {
        return copyOf(mediaList);
    }

    public void setEndList(boolean endList) {
        this.endList = endList;
    }

    public boolean hasEndList() {
        return endList;
    }

    public void addMedia(String url, double videoDurationSec) {
        mediaList.push(new Media(url, videoDurationSec));
    }

    public void setVersion(int v) {
        setHeader(VERSION, Integer.toString(v));
    }

    private void setHeader(String key, String value) {
        headers.$put(key, $array(value));
    }

    public int getVersion() {
        return toInt(getHeader(VERSION));
    }

    private int toInt(String val) {
        return JSGlobal.parseInt(val);
    }

    public void setTargetDuration(int sec) {
        setHeader(TARGETDURATION, Integer.toString(sec));
    }

    public int getTargetDuration() {
        return toInt(getHeader(TARGETDURATION));
    }

    public void setPlaylistType(String string) {
        setHeader(PLAYLIST_TYPE, string);
    }

    public String getPlaylistType() {
        return getHeader(PLAYLIST_TYPE);
    }

    private String getHeader(String key) {
        if (containsKey(key)) {
            return headers.$get(key).$get(0);
        }
        return null;
    }

    private boolean containsKey(String key) {
        return hasOwnProperty(headers, key);
    }

    public void setMediaSequence(int ms) {
        setHeader(MEDIA_SEQUENCE, Integer.toString(ms));
    }

    public int getMediaSequence() {
        return toInt(getHeader(MEDIA_SEQUENCE));
    }

    public void setAllowCache(boolean b) {
        setHeader(ALLOW_CACHE, b ? YES : NO);
    }

    public Boolean getAllowCache() {
        return toBooleanObject(getHeader(ALLOW_CACHE));
    }

    private static Boolean toBooleanObject(String val) {
        if (YES.equals(val)) {
            return true;
        } else if (NO.equals(val)) {
            return false;
        }
        return null;
    }

    protected static String attrsToString(Map<String, String> attrs) {
        String str = "";
        for (String key : attrs) {
            if ("" != str) {
                str += ",";
            }
            String val = attrs.$get(key);
            if (val != null) {
                str += ("=" + val);
            }
        }
        return str;
    }

    public static HLSPlaylist parseString(String str) {
        return parseM3U8($castArray(str.replaceAll("\r\n", "\n").split("\n")));
    }

    public static HLSPlaylist parseM3U8(Array<String> lines) {
        String line = lines.$length() > 0 ? lines.shift() : null;
        if (!EXTM3U.equals(line)) {
            return null;
        }
        HLSPlaylist playlist = new HLSPlaylist();
        boolean names = false;
        boolean variants = false;
        double extinf = -1.;
        while (lines.$length() > 0) {
            line = lines.shift();
            if (line.trim() == "") continue;
            if (line.startsWith("#")) {
                if (line.startsWith(EXTINF)) {
                    Array<String> split = split(line);
                    extinf = Double.parseDouble(split.$get(1).replaceAll(",$", ""));
                    names = true;
                } else if (line.startsWith(_Variant)) {
                    Array<String> split = split(line);
                    Variant variant = new Variant();
                    variant.attributes = parseAttributeList(split.$get(1));
                    playlist.variantList.push(variant);
                    variants = true;
                } else if (line.startsWith(ENDLIST)) {
                    playlist.endList = true;
                } else if (line.startsWith("#EXT-X")) {
                    Array<String> split = split(line);
                    String key = split.$get(0);
                    String value = split.$get(1);
                    playlist.addHeader(key, value);
                }
            } else if (names) {
                playlist.addMedia(line, extinf);
            } else if (variants) {
                playlist.variantList.$get(playlist.variantList.$length() - 1).uri = line;
            } else {
                console.error("wtf is going on?", line);
            }
        }
        return playlist;
    }

    private static Array<String> split(String line) {
        int idx = line.indexOf(":");
        if (idx < 0) {
            return $array(line);
        }
        return $array(line.substring(0, idx), line.substring(idx + 1));
    }

    public void setStart(float timeoffset, boolean precise) {
        setHeader(START, String.format("TIME-OFFSET=%f,PRECISE=%s", timeoffset, precise ? YES : NO));
    }

    public Pair<Float, Boolean> getStart() {
        String string = getHeader(START);
        if (string == null)
            return null;
        Map<String, String> map = parseAttributeList(string);
        return Pair.of(toFloatObject(map.$get("TIME-OFFSET")), toBooleanObject(map.$get("PRECISE")));
    }

    static Map<String, String> parseAttributeList(String string) {
        Map<String, String> map = $map();
        RegExp m = new RegExp("(([^,]+)=(\".+?\"|[^,]+)),?", "g");
        Array<String> myArray = $array();
        while ((myArray = m.exec(string)) != null) {
            map.$put(myArray.$get(2), myArray.$get(3));
        }
        return map;
    }

    private static Float toFloatObject(String string) {
        try {
            return Float.parseFloat(string);
        } catch (Exception e) {
            return null;
        }
    }

    public void addRelatedPlaylist(Map<String, String> attrs) {
        addHeader(Related, attrsToString(attrs));
    }

    private void addHeader(String key, String value) {
        if (!containsKey(key)) {
            headers.$put(key, $array());
        }
        headers.$get(key).push(value);
    }

    public void addVariantStream(Variant variant) {
        variantList.push(variant);
    }

    public static String quote(String uri) {
        return '"' + uri + '"';
    }

    public static Map<String, String> audio(String name, String uri, String groupId) {
        Map<String, String> map = $map();
        map.$put(TYPE, AUDIO);
        map.$put(GROUP_ID, groupId);
        map.$put(groupId, quote(name));
        map.$put(URI, quote(uri));
        map.$put(TYPE, AUDIO);
        return map;
    }

    public static void testParse4() throws Exception {
        // @formatter:off
        Array<String> m3u8 = $array(
                "#EXTM3U",
                "#EXT-X-VERSION:4",
                "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"index_audio\",NAME=\"eredeti\",DEFAULT=NO,AUTOSELECT=YES,URI=\"AUD_MUL.m3u8\"",
                "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"index_audio\",NAME=\"magyar\",DEFAULT=YES,AUTOSELECT=YES,LANGUAGE=\"hu\",URI=\"AUD_HUN.m3u8\"",
                "#EXT-X-STREAM-INF:PROGRAM-ID=16,BANDWIDTH=2699680,RESOLUTION=1280x720,CODECS=\"avc1.4d401f,mp4a.40.2\",AUDIO=\"index_audio\"",
                "VID_1280x720.m3u8",
                "#EXT-X-STREAM-INF:PROGRAM-ID=16,BANDWIDTH=2185312,RESOLUTION=1024x576,CODECS=\"avc1.4d401f,mp4a.40.2\",AUDIO=\"index_audio\"",
                "VID_640x360_1.m3u8",
                "#EXT-X-STREAM-INF:PROGRAM-ID=16,BANDWIDTH=645216,RESOLUTION=384x216,CODECS=\"avc1.42e015,mp4a.40.2\",AUDIO=\"index_audio\"",
                "VID_384x216.m3u8"
        );
        // @formatter:on
        console.log(HLSPlaylist.parseM3U8(m3u8));

        // @formatter:off
        Array<String> endlist = $array(
                "#EXTM3U",
                "#EXT-X-PLAYLIST-TYPE:VOD",
                "#EXT-X-TARGETDURATION:3",
                "#EXT-X-VERSION:3",
                "#EXTINF:2.13547,",
                "0.ts",
                "#EXTINF:2.13547,",
                "1.ts",
                "#EXTINF:2.13547,",
                "2.ts",
                "#EXT-X-ENDLIST"
        );
        // @formatter:on
        console.log(HLSPlaylist.parseM3U8(endlist));

    }

}
