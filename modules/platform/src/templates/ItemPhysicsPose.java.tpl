package dev.nightclient.platform;

public final class ItemPhysicsPose {
    public static final ItemPhysicsPose NONE=new ItemPhysicsPose(false,false,0,0,0);
    public final boolean enabled,grounded;
    public final float x,y,z;
    public ItemPhysicsPose(boolean enabled,boolean grounded,float x,float y,float z){this.enabled=enabled;this.grounded=grounded;this.x=x;this.y=y;this.z=z;}
}
