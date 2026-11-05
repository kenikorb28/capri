using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Windows.Forms;

namespace CapriDesktop
{
    // Her look, shared by Capri and her installer: her Mainframe's ground with a wine glow at the top, corners clipped big at
    // the top left and bottom right with a red rim and hot red strokes over the two big corners, rose titles and Windows' own
    // Bahnschrift. The Mainframe, her menus, her type box, the installer and her notices all draw from here.
    internal static class Neon
    {
        internal static readonly Color Ground = Color.FromArgb(10, 10, 16);
        internal static readonly Color Glow = Color.FromArgb(54, 14, 26);
        internal static readonly Color Band = Color.FromArgb(16, 12, 20);
        internal static readonly Color Rule = Color.FromArgb(44, 122, 134);
        internal static readonly Color Rose = Color.FromArgb(255, 93, 102);
        internal static readonly Color White = Color.FromArgb(236, 236, 241);
        internal static readonly Color Faint = Color.FromArgb(146, 130, 140);
        internal static readonly Color Field = Color.FromArgb(17, 16, 28);
        internal static readonly Color Hover = Color.FromArgb(30, 20, 36);
        internal static readonly Color Pressed = Color.FromArgb(46, 22, 38);
        internal static readonly Color Edge = Color.FromArgb(92, 24, 36);
        internal static readonly Color Hot = Color.FromArgb(232, 66, 70);
        internal static readonly Color Cyan = Color.FromArgb(94, 240, 246);
        internal static readonly Color Deep = Color.FromArgb(9, 30, 33);
        internal static readonly Color DeepEdge = Color.FromArgb(40, 120, 132);
        internal static readonly Color OnInk = Color.FromArgb(6, 14, 18);
        internal static readonly Color Dash = Color.FromArgb(78, 28, 40);
        internal static readonly Color Lit = Color.FromArgb(245, 90, 86);
        internal static readonly Color Brim = Color.FromArgb(120, 30, 44);
        internal static readonly Color Well = Color.FromArgb(12, 11, 20);
        internal static readonly Color WellEdge = Color.FromArgb(72, 28, 40);

        // The wine glow's falloff: strong at the top, gone well before the middle.
        internal static readonly Blend Fade = Falloff();

        private static Blend Falloff()
        {
            Blend blend = new Blend(9);
            for (int i = 0; i < 9; i++)
            {
                float at = i / 8f;
                blend.Positions[i] = at;
                blend.Factors[i] = 1 - (1 - at) * (1 - at);
            }
            return blend;
        }

        private static readonly FontFamily Plain = Family("Bahnschrift SemiCondensed");
        private static readonly FontFamily Heavy = Family("Bahnschrift SemiBold SemiConden");
        private static readonly FontFamily Narrow = Family("Bahnschrift SemiBold Condensed");

        private static FontFamily Family(string name)
        {
            try { return new FontFamily(name); }
            catch (ArgumentException) { return null; }
        }

        // Bahnschrift's semi-condensed cut, or its semi-bold for buttons and labels. Windows without it get Segoe UI.
        internal static Font Type(float size, bool bold = false, GraphicsUnit unit = GraphicsUnit.Point)
        {
            FontFamily family = bold ? Heavy : Plain;
            return family != null ? new Font(family, size, FontStyle.Regular, unit) : new Font("Segoe UI", size - 0.5f, bold ? FontStyle.Bold : FontStyle.Regular, unit);
        }

        // A window's title, like MAINFRAME: Bahnschrift's condensed semi-bold.
        internal static Font Display(float size, GraphicsUnit unit = GraphicsUnit.Point)
        {
            return Narrow != null ? new Font(Narrow, size, FontStyle.Regular, unit) : new Font("Segoe UI Semibold", size - 2f, FontStyle.Regular, unit);
        }

        // A box with each corner clipped by its own amount, top left first and on round the clock.
        internal static GraphicsPath Cut(RectangleF box, float topLeft, float topRight, float bottomRight, float bottomLeft)
        {
            GraphicsPath path = new GraphicsPath();
            path.AddPolygon(new[]
            {
                new PointF(box.Left + topLeft, box.Top), new PointF(box.Right - topRight, box.Top), new PointF(box.Right, box.Top + topRight),
                new PointF(box.Right, box.Bottom - bottomRight), new PointF(box.Right - bottomRight, box.Bottom), new PointF(box.Left + bottomLeft, box.Bottom),
                new PointF(box.Left, box.Bottom - bottomLeft), new PointF(box.Left, box.Top + topLeft)
            });
            return path;
        }

        // A panel's edge: corners clipped big at the top left and bottom right and small at the others, a dark red line round
        // it, and hot red strokes over the two big corners, reaching `along` the top and bottom and `down` the sides.
        internal static void Frame(Graphics graphics, Size size, float s, float big, float small, float along, float down)
        {
            SmoothingMode was = graphics.SmoothingMode;
            graphics.SmoothingMode = SmoothingMode.AntiAlias;
            RectangleF box = new RectangleF(0.5f * s, 0.5f * s, size.Width - s - 1, size.Height - s - 1);
            using (GraphicsPath edge = Cut(box, big, small, big, small))
            using (Pen line = new Pen(Brim, Math.Max(1, 1.2f * s)))
                graphics.DrawPath(line, edge);
            RectangleF hot = RectangleF.Inflate(box, -0.6f * s, -0.6f * s);
            using (Pen stroke = new Pen(Hot, 2 * s))
            {
                graphics.DrawLines(stroke, new[] { new PointF(hot.Left, hot.Top + big + down), new PointF(hot.Left, hot.Top + big), new PointF(hot.Left + big, hot.Top), new PointF(hot.Left + big + along, hot.Top) });
                graphics.DrawLines(stroke, new[] { new PointF(hot.Right, hot.Bottom - big - down), new PointF(hot.Right, hot.Bottom - big), new PointF(hot.Right - big, hot.Bottom), new PointF(hot.Right - big - along, hot.Bottom) });
            }
            graphics.SmoothingMode = was;
        }

        // Cuts a window to its clipped corners.
        internal static void Clip(Control window, float big, float small)
        {
            Region old = window.Region;
            using (GraphicsPath hull = Cut(new RectangleF(0, 0, window.Width, window.Height), big, small, big, small)) window.Region = new Region(hull);
            if (old != null) old.Dispose();
        }

        // Her ground with the wine glow fading out over `reach` from the top.
        internal static void Backdrop(Graphics graphics, Size size, float reach)
        {
            using (Brush ground = new SolidBrush(Ground)) graphics.FillRectangle(ground, 0, 0, size.Width, size.Height);
            float glow = Math.Min(size.Height, reach);
            if (glow < 1) return;
            using (LinearGradientBrush wine = new LinearGradientBrush(new RectangleF(0, -1, 16, glow + 2), Glow, Ground, LinearGradientMode.Vertical))
            {
                wine.Blend = Fade;
                graphics.FillRectangle(wine, 0, 0, size.Width, glow);
            }
        }

        // What's behind a control on one of her windows, measured from the window so it reads as cut out of one panel.
        internal static void Behind(Graphics graphics, Control control, float reach)
        {
            Form window = control.FindForm();
            if (window == null) { Backdrop(graphics, control.Size, reach); return; }
            int x = 0, y = 0;
            for (Control at = control; at != null && at != window; at = at.Parent) { x += at.Left; y += at.Top; }
            GraphicsState state = graphics.Save();
            graphics.TranslateTransform(-x, -y);
            Backdrop(graphics, window.ClientSize, reach);
            graphics.Restore(state);
        }

        // A label that lets the mouse through to its window, so the window drags from anywhere that isn't a control.
        internal sealed class Caption : Label
        {
            internal Caption()
            {
                BackColor = Color.Transparent;
                UseMnemonic = false;
            }

            protected override void WndProc(ref Message message)
            {
                if (message.Msg == 0x0084) { message.Result = new IntPtr(-1); return; }
                base.WndProc(ref message);
            }
        }
    }

    // A button in her look: a dark box with a red rim and its bottom right corner clipped. A cyan one is the step to take, and
    // a solid one is the window's main step; a red one ends something. Glyph 1 or 2 makes it a bare minimize or close mark.
    internal sealed class Plate : Button
    {
        internal Color Accent = Neon.Rose;
        internal bool Solid;
        internal int Glyph;
        private bool hot;
        private bool down;

        internal Plate()
        {
            SetStyle(ControlStyles.OptimizedDoubleBuffer | ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint, true);
            BackColor = Neon.Ground;
            Cursor = Cursors.Hand;
            UseMnemonic = false;
        }

        protected override void OnMouseEnter(EventArgs e) { hot = true; Invalidate(); base.OnMouseEnter(e); }

        protected override void OnMouseLeave(EventArgs e) { hot = false; down = false; Invalidate(); base.OnMouseLeave(e); }

        protected override void OnMouseDown(MouseEventArgs e) { if (e.Button == MouseButtons.Left) { down = true; Invalidate(); } base.OnMouseDown(e); }

        protected override void OnMouseUp(MouseEventArgs e) { down = false; Invalidate(); base.OnMouseUp(e); }

        protected override void OnPaint(PaintEventArgs e)
        {
            float s = DeviceDpi / 96f;
            Graphics graphics = e.Graphics;
            Neon.Behind(graphics, this, 210 * s);
            graphics.SmoothingMode = SmoothingMode.AntiAlias;
            float line = Math.Max(1f, s);
            RectangleF box = new RectangleF(line / 2, line / 2, Width - line, Height - line);
            float x = Width / 2f;
            float y = Height / 2f;
            if (Glyph != 0)
            {
                if (hot) using (GraphicsPath plate = Neon.Cut(box, 0, 0, 4 * s, 0)) using (Brush fill = new SolidBrush(down ? Neon.Pressed : Neon.Hover)) graphics.FillPath(fill, plate);
                using (Pen pen = new Pen(hot || (Focused && ShowFocusCues) ? (Glyph == 2 ? Neon.Hot : Neon.Cyan) : Neon.Faint, 1.5f * s))
                {
                    if (Glyph == 1) graphics.DrawLine(pen, x - 5 * s, y, x + 5 * s, y);
                    else
                    {
                        graphics.DrawLine(pen, x - 4.5f * s, y - 4.5f * s, x + 4.5f * s, y + 4.5f * s);
                        graphics.DrawLine(pen, x - 4.5f * s, y + 4.5f * s, x + 4.5f * s, y - 4.5f * s);
                    }
                }
                return;
            }
            bool lit = hot || (Focused && ShowFocusCues);
            Color face, rim, ink;
            if (!Enabled) { face = Neon.Field; rim = Color.FromArgb(52, 22, 32); ink = Color.FromArgb(112, 94, 104); }
            else if (Solid) { face = down ? ControlPaint.Dark(Accent, 0.1f) : hot ? ControlPaint.Light(Accent, 0.35f) : Accent; rim = face; ink = Neon.OnInk; }
            else if (Accent == Neon.Cyan) { face = down ? Color.FromArgb(18, 62, 67) : hot ? Color.FromArgb(13, 45, 49) : Neon.Deep; rim = lit ? Neon.Cyan : Neon.DeepEdge; ink = Neon.Cyan; }
            else if (Accent == Neon.Hot) { face = down ? Color.FromArgb(72, 18, 30) : hot ? Color.FromArgb(54, 16, 28) : Color.FromArgb(34, 12, 22); rim = lit ? Color.FromArgb(255, 124, 122) : Neon.Hot; ink = Color.FromArgb(255, 166, 162); }
            else { face = down ? Neon.Pressed : hot ? Neon.Hover : Neon.Field; rim = lit ? Neon.Hot : Neon.Edge; ink = Accent; }
            using (GraphicsPath plate = Neon.Cut(box, 0, 0, 6 * s, 0))
            {
                using (Brush brush = new SolidBrush(face)) graphics.FillPath(brush, plate);
                using (Pen pen = new Pen(rim, line)) graphics.DrawPath(pen, plate);
            }
            // A solid button shows keyboard focus as a dark line just inside its edge.
            if (Solid && Enabled && Focused && ShowFocusCues)
                using (GraphicsPath inner = Neon.Cut(RectangleF.Inflate(box, -3 * s, -3 * s), 0, 0, 4 * s, 0))
                using (Pen pen = new Pen(Neon.OnInk, line))
                    graphics.DrawPath(pen, inner);
            TextRenderer.DrawText(graphics, Text, Font, ClientRectangle, ink, TextFormatFlags.VerticalCenter | TextFormatFlags.HorizontalCenter | TextFormatFlags.SingleLine | TextFormatFlags.NoPrefix | TextFormatFlags.EndEllipsis);
        }
    }
}
