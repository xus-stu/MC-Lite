package dev.mclite.island.bili;

/** B 站图片地址的小工具：补协议、去掉旧尺寸后缀、换成指定尺寸的 JPEG。 */
public final class BiliImageRef {

    private BiliImageRef() {
    }

    public static String sized(String url, int width, int height, boolean crop) {
        if (url == null || url.isBlank() || width <= 0 || height <= 0) {
            return "";
        }
        String value = url.trim();
        if (value.startsWith("//")) {
            value = "https:" + value;
        } else if (value.startsWith("http://")) {
            value = "https://" + value.substring("http://".length());
        }
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int at = value.indexOf('@');
        if (at > 0) {
            value = value.substring(0, at);
        }
        return value + "@" + width + "w_" + height + "h" + (crop ? "_1c" : "") + ".jpg";
    }
}
