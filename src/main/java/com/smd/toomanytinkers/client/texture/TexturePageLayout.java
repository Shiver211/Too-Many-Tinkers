package com.smd.toomanytinkers.client.texture;

/** 只分配页内矩形；整页淘汰时重新建立，已有矩形不会移动。 */
public final class TexturePageLayout {
    private final int width;
    private final int height;
    private final int alignment;
    private int x;
    private int y;
    private int rowHeight;

    public TexturePageLayout(int width, int height, int alignment) {
        this.width = width;
        this.height = height;
        this.alignment = alignment;
    }

    public Region allocate(int imageWidth, int imageHeight) {
        int w = align(imageWidth);
        int h = align(imageHeight);
        if (w > width || h > height) return null;
        int nextX = x;
        int nextY = y;
        int nextRowHeight = rowHeight;
        if (nextX + w > width) {
            nextX = 0;
            nextY += nextRowHeight;
            nextRowHeight = 0;
        }
        if (nextY + h > height) return null;
        Region result = new Region(nextX, nextY, imageWidth, imageHeight);
        x = nextX + w;
        y = nextY;
        rowHeight = Math.max(nextRowHeight, h);
        return result;
    }

    private int align(int value) {
        return (value + alignment - 1) / alignment * alignment;
    }

    public record Region(int x, int y, int width, int height) {}
}
