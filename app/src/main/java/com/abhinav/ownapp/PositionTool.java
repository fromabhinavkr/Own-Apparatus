package com.abhinav.ownapp;

import android.app.AlertDialog; import android.content.Context; import android.graphics.Color; import android.graphics.drawable.GradientDrawable; import android.view.Gravity; import android.view.ViewGroup; import android.widget.Button; import android.widget.LinearLayout; import android.widget.TextView; import android.widget.Toast;

public class PositionTool {
    public static void showPositionDialog(Context context, ImageEditorActivity.PhotoEditorView editorView, boolean isDarkTheme, int panelColor, int textColor) {
        LayerSettingsUI.GraphicLayer activeLayer = editorView.getActiveLayer(); if (activeLayer == null) { Toast.makeText(context, "Select a layer first!", Toast.LENGTH_SHORT).show(); return; }
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.ModernDialogStyle); LinearLayout mainLayout = new LinearLayout(context); mainLayout.setOrientation(LinearLayout.VERTICAL); mainLayout.setPadding(40, 40, 40, 40);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(panelColor); gd.setCornerRadius(60f); mainLayout.setBackground(gd);
        TextView title = new TextView(context); title.setText("Position Layer"); title.setTextSize(20f); title.setTextColor(textColor); title.setGravity(Gravity.CENTER); title.setPadding(0, 0, 0, 32); mainLayout.addView(title);

        LinearLayout gridContainer = new LinearLayout(context); gridContainer.setOrientation(LinearLayout.VERTICAL); gridContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String[] positions = { "Top Left", "Top", "Top Right", "Left", "Center", "Right", "Bottom Left", "Bottom", "Bottom Right" };
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(context); row.setOrientation(LinearLayout.HORIZONTAL); row.setWeightSum(3f); row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            for (int c = 0; c < 3; c++) {
                int posIndex = r * 3 + c;
                Button btn = new Button(context); btn.setText(positions[posIndex]); btn.setTextSize(12f); btn.setTextColor(textColor); btn.setAllCaps(false);
                GradientDrawable btnBg = new GradientDrawable(); btnBg.setColor(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA")); btnBg.setCornerRadius(20f); btn.setBackground(btnBg);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); params.setMargins(8, 8, 8, 8); btn.setLayoutParams(params);
                btn.setOnClickListener(v -> {
                    if (editorView.baseImage == null) return;
                    float baseW = editorView.baseImage.getWidth(); float baseH = editorView.baseImage.getHeight();
                    float layerW = activeLayer.bounds.width() * activeLayer.scaleX; float layerH = activeLayer.bounds.height() * activeLayer.scaleY;
                    float leftX = -baseW / 2f + layerW / 2f; float rightX = baseW / 2f - layerW / 2f; float centerX = 0f;
                    float topY = -baseH / 2f + layerH / 2f; float bottomY = baseH / 2f - layerH / 2f; float centerY = 0f;
                    switch (posIndex) { case 0: activeLayer.x = leftX; activeLayer.y = topY; break; case 1: activeLayer.x = centerX; activeLayer.y = topY; break; case 2: activeLayer.x = rightX; activeLayer.y = topY; break; case 3: activeLayer.x = leftX; activeLayer.y = centerY; break; case 4: activeLayer.x = centerX; activeLayer.y = centerY; break; case 5: activeLayer.x = rightX; activeLayer.y = centerY; break; case 6: activeLayer.x = leftX; activeLayer.y = bottomY; break; case 7: activeLayer.x = centerX; activeLayer.y = bottomY; break; case 8: activeLayer.x = rightX; activeLayer.y = bottomY; break; }
                    editorView.invalidate();
                }); row.addView(btn);
            } gridContainer.addView(row);
        } mainLayout.addView(gridContainer);
        Button btnClose = new Button(context); btnClose.setText("Close"); btnClose.setTextColor(Color.WHITE); GradientDrawable closeBg = new GradientDrawable(); closeBg.setColor(Color.parseColor("#FF3B30")); closeBg.setCornerRadius(30f); btnClose.setBackground(closeBg);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120); closeLp.setMargins(0, 32, 0, 0); btnClose.setLayoutParams(closeLp);
        builder.setView(mainLayout); AlertDialog dialog = builder.create(); if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        btnClose.setOnClickListener(v -> dialog.dismiss()); mainLayout.addView(btnClose); dialog.show();
    }
}