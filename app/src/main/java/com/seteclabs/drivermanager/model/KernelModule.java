package com.seteclabs.drivermanager.model;

/**
 * Represents a .ko kernel module file.
 */
public class KernelModule {
    private String filename;
    private String name;
    private String description;
    private String version;
    private String author;
    private String depends;
    private String vermagic;
    private long size;
    private boolean loaded;
    private boolean autoload;
    private boolean compatible;

    public KernelModule() {}

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getDepends() { return depends; }
    public void setDepends(String depends) { this.depends = depends; }
    public String getVermagic() { return vermagic; }
    public void setVermagic(String vermagic) { this.vermagic = vermagic; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public boolean isLoaded() { return loaded; }
    public void setLoaded(boolean loaded) { this.loaded = loaded; }
    public boolean isAutoload() { return autoload; }
    public void setAutoload(boolean autoload) { this.autoload = autoload; }
    public boolean isCompatible() { return compatible; }
    public void setCompatible(boolean compatible) { this.compatible = compatible; }
}
