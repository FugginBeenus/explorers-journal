package com.fugginbeenus.explorersjournal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class EJScreen extends Screen {
    protected EJScreen(Component title) {
        super(title);
    }

    protected abstract void ejDraw(GuiGraphics ctx, int mouseX, int mouseY);

    protected boolean ejClick(double mx, double my, int button) {
        return false;
    }

    protected boolean ejScroll(double mx, double my, double amount) {
        return false;
    }

    protected boolean ejKey(int keyCode) {
        return false;
    }

    protected boolean ejDrag(double mx, double my, int button) {
        return false;
    }

    protected void ejRelease(double mx, double my, int button) {
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        return ejClick(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return ejDrag(mx, my, button) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        ejRelease(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return ejKey(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    //? if >=1.21 {
    /*@Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double amount) {
        return ejScroll(mx, my, amount) || super.mouseScrolled(mx, my, horizontalAmount, amount);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
    }
    *///?} else {
    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return ejScroll(mx, my, amount) || super.mouseScrolled(mx, my, amount);
    }
    //?}

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ejDraw(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
