package com.example.pccell;

public class RemoteDirItem {
    public final boolean isDir;
    public final String name;
    public final String fullPath;
    public final long size;

    public RemoteDirItem(boolean isDir, String name, String fullPath, long size) {
        this.isDir = isDir;
        this.name = name;
        this.fullPath = fullPath;
        this.size = size;
    }
}
