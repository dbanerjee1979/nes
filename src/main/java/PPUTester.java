import experiments.nes.FileBus;
import experiments.nes.ppu.NesPalette;
import experiments.nes.ppu.PPU;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PPUTester {
    public static void main(String[] args) {
        PPU ppu = new PPU(new FileBus("/ppu_memory.bin", 0x4000), new NesPalette());
        ppu.setBackgroundPatternTable(1);

        for (int i = 0; i < 341; i++) {
            ppu.cycle();
        }

        for (int scanline = 0; scanline < 239; scanline++) {
            for (int i = 0; i < 341; i++) {
                ppu.cycle();
            }
        }

        JFrame frame = new JFrame();
        frame.setContentPane(createImagePanel(ppu.getFrameBuffer(), 512, 480));
        frame.setSize(512, 480);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    @SuppressWarnings("SameParameterValue")
    private static JPanel createImagePanel(BufferedImage patternTable, int width, int height) {
        JPanel patternTablePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(patternTable, 0, 0, width, height, null);
            }
        };
        patternTablePanel.setPreferredSize(new Dimension(width, height));
        return patternTablePanel;
    }
}
