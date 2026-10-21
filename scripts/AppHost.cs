using System;
using System.IO;
using System.Net;
using System.Text;
using System.Threading;
using System.Diagnostics;
using System.Windows.Forms;
using System.Drawing;
using System.Collections.Generic;

namespace IPTVDataArchitect
{
    class SplashForm : Form
    {
        private float actualProgress = 0f;
        private float displayProgress = 0f;
        private string statusMessage = "Inicializando núcleo...";
        private System.Windows.Forms.Timer animTimer;

        public void UpdateProgress(float p, string msg)
        {
            this.actualProgress = p;
            this.statusMessage = msg;
        }

        public SplashForm()
        {
            this.FormBorderStyle = FormBorderStyle.None;
            this.StartPosition = FormStartPosition.CenterScreen;
            this.Size = new Size(800, 450); 
            this.ShowInTaskbar = false;
            this.DoubleBuffered = true; 

            try {
                string exeDir = AppDomain.CurrentDomain.BaseDirectory;
                string imgPath = Path.Combine(exeDir, "iptv-app", "splash_bg.jpg");
                if (File.Exists(imgPath)) {
                    this.BackgroundImage = Image.FromFile(imgPath);
                    this.BackgroundImageLayout = ImageLayout.Stretch;
                }
            } catch {}

            animTimer = new System.Windows.Forms.Timer();
            animTimer.Interval = 16; 
            animTimer.Tick += (s, e) => {
                displayProgress += (actualProgress - displayProgress) * 0.15f;
                if (displayProgress > 0.999f) displayProgress = 1f;
                this.Invalidate();
            };
            animTimer.Start();

            this.Paint += (s, e) => {
                Graphics g = e.Graphics;
                g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.HighQuality;
                g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;

                if (this.BackgroundImage == null)
                {
                    g.Clear(Color.FromArgb(10, 5, 20));
                }

                using (Pen borderPen = new Pen(Color.FromArgb(0, 217, 255), 1f))
                {
                    g.DrawRectangle(borderPen, 0, 0, this.Width - 1, this.Height - 1);
                }

                using (Font iptvFont = new Font("Segoe UI", 8, FontStyle.Bold))
                using (Brush iptvBrush = new SolidBrush(Color.FromArgb(150, 150, 180)))
                {
                    g.DrawString("▷ IPTV", iptvFont, iptvBrush, this.Width - 60, 15);
                }

                int cx = this.Width / 2;

                using (System.Drawing.Drawing2D.GraphicsPath crown = new System.Drawing.Drawing2D.GraphicsPath())
                {
                    int cy = 110;
                    crown.AddLine(cx - 30, cy - 10, cx - 40, cy - 40);
                    crown.AddLine(cx - 40, cy - 40, cx - 15, cy - 20);
                    crown.AddLine(cx - 15, cy - 20, cx, cy - 45);
                    crown.AddLine(cx, cy - 45, cx + 15, cy - 20);
                    crown.AddLine(cx + 15, cy - 20, cx + 40, cy - 40);
                    crown.AddLine(cx + 40, cy - 40, cx + 30, cy - 10);
                    
                    using (Pen crownPen = new Pen(Color.FromArgb(189, 101, 255), 2f)) 
                    {
                        crownPen.LineJoin = System.Drawing.Drawing2D.LineJoin.Round;
                        g.DrawPath(crownPen, crown);
                    }
                }

                string title = "LELOUCH";
                using (Font titleFont = new Font("Segoe UI", 48, FontStyle.Bold))
                using (Brush titleBrush = new SolidBrush(Color.FromArgb(248, 250, 255)))
                {
                    SizeF tSize = g.MeasureString(title, titleFont);
                    g.DrawString(title, titleFont, titleBrush, cx - (tSize.Width / 2), 125);
                }

                string subtitle = "R E P R O D U C T O R   I P T V";
                using (Font subFont = new Font("Segoe UI", 10, FontStyle.Bold))
                using (Brush subBrush = new SolidBrush(Color.FromArgb(189, 101, 255)))
                {
                    SizeF sSize = g.MeasureString(subtitle, subFont);
                    g.DrawString(subtitle, subFont, subBrush, cx - (sSize.Width / 2), 205);
                }

                int barWidth = 480;
                int barHeight = 4;
                int barX = cx - (barWidth / 2);
                int barY = 270;

                using (Pen trackPen = new Pen(Color.FromArgb(0, 217, 255), 1f))
                {
                    g.DrawRectangle(trackPen, barX - 1, barY - 1, barWidth + 1, barHeight + 1);
                }

                int fillWidth = (int)(barWidth * displayProgress);
                if (fillWidth > 0)
                {
                    using (var fillBrush = new System.Drawing.Drawing2D.LinearGradientBrush(
                        new Rectangle(barX, barY, Math.Max(fillWidth, 1), barHeight),
                        Color.FromArgb(0, 100, 255),
                        Color.FromArgb(0, 217, 255),
                        0f))
                    {
                        g.FillRectangle(fillBrush, barX, barY, fillWidth, barHeight);
                    }
                    
                    using (System.Drawing.Drawing2D.GraphicsPath path = new System.Drawing.Drawing2D.GraphicsPath())
                    {
                        int glowRadius = 40;
                        path.AddEllipse(barX + fillWidth - (glowRadius/2), barY + (barHeight/2) - (glowRadius/2), glowRadius, glowRadius);
                        using (var pgb = new System.Drawing.Drawing2D.PathGradientBrush(path))
                        {
                            pgb.CenterColor = Color.FromArgb(150, 0, 217, 255);
                            pgb.SurroundColors = new Color[] { Color.Transparent };
                            g.FillEllipse(pgb, barX + fillWidth - (glowRadius/2), barY + (barHeight/2) - (glowRadius/2), glowRadius, glowRadius);
                        }
                    }
                }

                using (Font statFont = new Font("Segoe UI", 9, FontStyle.Regular))
                using (Brush statBrush = new SolidBrush(Color.FromArgb(200, 200, 220)))
                {
                    SizeF stSize = g.MeasureString(statusMessage, statFont);
                    g.DrawString(statusMessage, statFont, statBrush, cx - (stSize.Width / 2), barY + 20);
                }

                string pct = (int)(displayProgress * 100) + "%";
                using (Font pctFont = new Font("Segoe UI", 9, FontStyle.Bold))
                using (Brush pctBrush = new SolidBrush(Color.FromArgb(0, 217, 255)))
                {
                    SizeF pSize = g.MeasureString(pct, pctFont);
                    g.DrawString(pct, pctFont, pctBrush, cx - (pSize.Width / 2), barY + 40);
                }

                int iconY = 380;
                using (Font iconFont = new Font("Segoe UI", 8, FontStyle.Regular))
                using (Brush iconBrush = new SolidBrush(Color.FromArgb(100, 100, 130)))
                {
                    string[] icons = { "📺 TV", "🎬 PELÍCULAS", "🎞️ SERIES", "⭐ FAVORITOS" };
                    int spacing = 120;
                    int startX = cx - (int)(spacing * 1.5f);
                    
                    for (int i = 0; i < icons.Length; i++)
                    {
                        SizeF icSize = g.MeasureString(icons[i], iconFont);
                        g.DrawString(icons[i], iconFont, iconBrush, startX + (spacing * i) - (icSize.Width / 2), iconY);
                    }
                }
            };
        }
    }

    static class Program
    {
        private static HttpListener webListener;
        private static HttpListener proxyListener;
        private static int webPort = 8686;
        private static int proxyPort = 7878;
        private static string appRoot;

        private static readonly Dictionary<string, string> mimeTypes = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            { ".html", "text/html; charset=utf-8" },
            { ".css",  "text/css; charset=utf-8" },
            { ".js",   "application/javascript; charset=utf-8" },
            { ".json", "application/json; charset=utf-8" },
            { ".png",  "image/png" },
            { ".jpg",  "image/jpeg" },
            { ".svg",  "image/svg+xml" },
            { ".ico",  "image/x-icon" },
            { ".webp", "image/webp" }
        };

        [STAThread]
        static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            
            SplashForm splash = new SplashForm();

            Thread initThread = new Thread(() => {
                string exeDir = AppDomain.CurrentDomain.BaseDirectory;
                string subDir = Path.Combine(exeDir, "iptv-app");
                appRoot = Directory.Exists(subDir) ? subDir : exeDir;

                splash.Invoke(new Action(() => splash.UpdateProgress(0.15f, "Inicializando núcleo...")));
                Thread.Sleep(500);

                splash.Invoke(new Action(() => splash.UpdateProgress(0.35f, "Preparando servicios multimedia...")));
                webListener = BindListener(ref webPort, 8686, 8700);
                proxyListener = BindListener(ref proxyPort, 7878, 7890);
                Thread.Sleep(500);

                if (webListener != null && proxyListener != null)
                {
                    splash.Invoke(new Action(() => splash.UpdateProgress(0.60f, "Estableciendo túneles de red...")));
                    try
                    {
                        string configPath = Path.Combine(appRoot, "src", "proxy-config.js");
                        string configDir = Path.GetDirectoryName(configPath);
                        if (!Directory.Exists(configDir)) Directory.CreateDirectory(configDir);
                        File.WriteAllText(configPath, "window.IPTV_PROXY_PORT = " + proxyPort + ";", Encoding.UTF8);
                    }
                    catch {}

                    Thread tWeb = new Thread(RunWebServer) { IsBackground = true };
                    tWeb.Start();

                    Thread tProxy = new Thread(RunProxyServer) { IsBackground = true };
                    tProxy.Start();
                    Thread.Sleep(400);
                }

                splash.Invoke(new Action(() => splash.UpdateProgress(0.85f, "Desplegando interfaz...")));
                Thread.Sleep(500);

                splash.Invoke(new Action(() => splash.UpdateProgress(0.95f, "Finalizando inicialización...")));
                string appUrl = "http://localhost:" + webPort;
                OpenPreferredBrowser(appUrl);
                Thread.Sleep(600);

                splash.Invoke(new Action(() => {
                    splash.UpdateProgress(1.0f, "Sistema preparado");
                    splash.Close();
                }));
            });
            initThread.IsBackground = true;
            initThread.Start();

            Application.Run(splash);

            Thread.Sleep(Timeout.Infinite);
        }

        static void OpenPreferredBrowser(string url)
        {
            string[] browserCandidates = new string[]
            {
                @"C:\Program Files\BraveSoftware\Brave-Browser\Application\brave.exe",
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"BraveSoftware\Brave-Browser\Application\brave.exe"),
                @"C:\Program Files\Google\Chrome\Application\chrome.exe",
                @"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"Google\Chrome\Application\chrome.exe"),
                @"C:\Program Files\Mozilla Firefox\firefox.exe",
                @"C:\Program Files (x86)\Mozilla Firefox\firefox.exe"
            };

            foreach (string b in browserCandidates)
            {
                if (File.Exists(b))
                {
                    try
                    {
                        ProcessStartInfo psi = new ProcessStartInfo();
                        psi.FileName = b;
                        psi.Arguments = "\"" + url + "\"";
                        psi.UseShellExecute = false;
                        Process.Start(psi);
                        return;
                    }
                    catch {}
                }
            }

            try { Process.Start(url); } catch {}
        }

        static HttpListener BindListener(ref int selectedPort, int startPort, int endPort)
        {
            for (int p = startPort; p <= endPort; p++)
            {
                try
                {
                    HttpListener l = new HttpListener();
                    l.Prefixes.Add("http://localhost:" + p + "/");
                    l.Start();
                    selectedPort = p;
                    return l;
                }
                catch { selectedPort++; }
            }
            return null;
        }

        static void RunWebServer()
        {
            while (webListener.IsListening)
            {
                try
                {
                    HttpListenerContext ctx = webListener.GetContext();
                    ThreadPool.QueueUserWorkItem((obj) => HandleWebRequest(ctx));
                }
                catch { break; }
            }
        }

        static string FindVlcPath()
        {
            string[] candidates = new string[]
            {
                @"C:\Program Files\VideoLAN\VLC\vlc.exe",
                @"C:\Program Files (x86)\VideoLAN\VLC\vlc.exe",
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"Programs\VideoLAN\VLC\vlc.exe")
            };

            foreach (string p in candidates)
            {
                if (File.Exists(p)) return p;
            }

            try
            {
                using (Microsoft.Win32.RegistryKey key = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\vlc.exe"))
                {
                    if (key != null)
                    {
                        object val = key.GetValue(null);
                        if (val != null && File.Exists(val.ToString())) return val.ToString();
                    }
                }
            }
            catch {}

            try
            {
                using (Microsoft.Win32.RegistryKey key = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(@"SOFTWARE\VideoLAN\VLC"))
                {
                    if (key != null)
                    {
                        object val = key.GetValue("InstallDir");
                        if (val != null)
                        {
                            string p = Path.Combine(val.ToString(), "vlc.exe");
                            if (File.Exists(p)) return p;
                        }
                    }
                }
            }
            catch {}

            return null;
        }

        static void HandleOpenVlcRequest(string streamUrl, HttpListenerResponse res)
        {
            string vlcPath = FindVlcPath();

            if (!string.IsNullOrEmpty(vlcPath))
            {
                try
                {
                    ProcessStartInfo psi = new ProcessStartInfo();
                    psi.FileName = vlcPath;
                    psi.Arguments = "\"" + (streamUrl ?? "") + "\"";
                    psi.UseShellExecute = false;
                    Process.Start(psi);

                    byte[] json = Encoding.UTF8.GetBytes("{\"success\":true,\"installed\":true,\"message\":\"VLC iniciado con éxito\"}");
                    res.ContentType = "application/json; charset=utf-8";
                    res.StatusCode = 200;
                    res.OutputStream.Write(json, 0, json.Length);
                }
                catch (Exception ex)
                {
                    byte[] json = Encoding.UTF8.GetBytes("{\"success\":false,\"installed\":true,\"message\":\"" + ex.Message.Replace("\"", "'") + "\"}");
                    res.ContentType = "application/json; charset=utf-8";
                    res.StatusCode = 500;
                    res.OutputStream.Write(json, 0, json.Length);
                }
            }
            else
            {
                byte[] json = Encoding.UTF8.GetBytes("{\"success\":false,\"installed\":false,\"message\":\"VLC Media Player no está instalado en este equipo.\",\"downloadUrl\":\"https://www.videolan.org/vlc/\"}");
                res.ContentType = "application/json; charset=utf-8";
                res.StatusCode = 200;
                res.OutputStream.Write(json, 0, json.Length);
            }
        }

        static void HandleWebRequest(HttpListenerContext ctx)
        {
            HttpListenerRequest req = ctx.Request;
            HttpListenerResponse res = ctx.Response;

            res.Headers.Add("Access-Control-Allow-Origin", "*");
            res.Headers.Add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            res.Headers.Add("Access-Control-Allow-Headers", "*");

            if (req.HttpMethod == "OPTIONS")
            {
                res.StatusCode = 204;
                try { res.Close(); } catch {}
                return;
            }

            try
            {
                if (req.Url.LocalPath.IndexOf("open-vlc", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    string streamUrl = req.QueryString["url"];
                    HandleOpenVlcRequest(streamUrl, res);
                    return;
                }

                string relPath = req.Url.LocalPath.TrimStart('/').Replace('/', '\\');
                if (string.IsNullOrEmpty(relPath)) relPath = "index.html";

                string fullPath = Path.Combine(appRoot, relPath);

                if (File.Exists(fullPath))
                {
                    string ext = Path.GetExtension(fullPath);
                    string mime = mimeTypes.ContainsKey(ext) ? mimeTypes[ext] : "application/octet-stream";

                    byte[] data = File.ReadAllBytes(fullPath);
                    res.ContentType = mime;
                    res.ContentLength64 = data.Length;
                    res.StatusCode = 200;
                    res.OutputStream.Write(data, 0, data.Length);
                }
                else
                {
                    byte[] err = Encoding.UTF8.GetBytes("404 - No encontrado");
                    res.ContentType = "text/plain; charset=utf-8";
                    res.StatusCode = 404;
                    res.OutputStream.Write(err, 0, err.Length);
                }
            }
            catch {}
            finally { try { res.Close(); } catch {} }
        }

        static void RunProxyServer()
        {
            while (proxyListener.IsListening)
            {
                try
                {
                    HttpListenerContext ctx = proxyListener.GetContext();
                    ThreadPool.QueueUserWorkItem((obj) => HandleProxyRequest(ctx));
                }
                catch { break; }
            }
        }

        static bool IsSafeTargetUri(Uri uri)
        {
            if (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps)
                return false;

            string host = uri.Host.ToLowerInvariant();
            if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "0.0.0.0")
                return false;

            IPAddress ip;
            if (IPAddress.TryParse(host, out ip))
            {
                byte[] bytes = ip.GetAddressBytes();
                if (bytes.Length == 4)
                {
                    if (bytes[0] == 10) return false;
                    if (bytes[0] == 172 && (bytes[1] >= 16 && bytes[1] <= 31)) return false;
                    if (bytes[0] == 192 && bytes[1] == 168) return false;
                    if (bytes[0] == 169 && bytes[1] == 254) return false;
                }
            }
            return true;
        }

        static void HandleProxyRequest(HttpListenerContext ctx)
        {
            HttpListenerRequest req = ctx.Request;
            HttpListenerResponse res = ctx.Response;

            res.Headers.Add("Access-Control-Allow-Origin", "*");
            res.Headers.Add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            res.Headers.Add("Access-Control-Allow-Headers", "*");

            if (req.HttpMethod == "OPTIONS")
            {
                res.StatusCode = 204;
                try { res.Close(); } catch {}
                return;
            }

            if (req.Url.LocalPath.IndexOf("open-vlc", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                string streamUrl = req.QueryString["url"] ?? req.QueryString["target"];
                HandleOpenVlcRequest(streamUrl, res);
                try { res.Close(); } catch {}
                return;
            }

            string targetEncoded = req.QueryString["target"];
            if (string.IsNullOrEmpty(targetEncoded))
            {
                byte[] err = Encoding.UTF8.GetBytes("{\"error\":\"Falta parametro target\"}");
                res.ContentType = "application/json";
                res.StatusCode = 400;
                res.OutputStream.Write(err, 0, err.Length);
                try { res.Close(); } catch {}
                return;
            }

            string targetUrl = Uri.UnescapeDataString(targetEncoded);
            if (targetUrl.IndexOf("redworld.pro:8888", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                targetUrl = targetUrl.Replace(":8888", ":8880");
            }
            else if (targetUrl.IndexOf("redworld.pro/", StringComparison.OrdinalIgnoreCase) >= 0 && targetUrl.IndexOf("redworld.pro:", StringComparison.OrdinalIgnoreCase) < 0)
            {
                targetUrl = targetUrl.Replace("redworld.pro/", "redworld.pro:8880/");
            }

            Uri targetUri;
            if (!Uri.TryCreate(targetUrl, UriKind.Absolute, out targetUri) || !IsSafeTargetUri(targetUri))
            {
                byte[] err = Encoding.UTF8.GetBytes("{\"error\":\"Destino no permitido por politica de seguridad anti-SSRF\"}");
                res.ContentType = "application/json";
                res.StatusCode = 403;
                res.OutputStream.Write(err, 0, err.Length);
                try { res.Close(); } catch {}
                return;
            }

            try
            {
                HttpWebRequest remoteReq = (HttpWebRequest)WebRequest.Create(targetUrl);
                remoteReq.UserAgent = "Mozilla/5.0 (VLC/3.0.18; IPTV-Data-Architect/1.0)";
                remoteReq.Timeout = 15000;
                remoteReq.ReadWriteTimeout = 120000;

                using (HttpWebResponse remoteRes = (HttpWebResponse)remoteReq.GetResponse())
                {
                    res.StatusCode = (int)remoteRes.StatusCode;

                    string mime = remoteRes.ContentType;
                    if (string.IsNullOrEmpty(mime) || mime.Contains("text/plain"))
                    {
                        if (targetUrl.Contains(".m3u8") || targetUrl.Contains("type=m3u")) mime = "application/vnd.apple.mpegurl";
                        else if (targetUrl.Contains(".ts")) mime = "video/mp2t";
                        else if (targetUrl.Contains(".mp4")) mime = "video/mp4";
                        else mime = "application/json; charset=utf-8";
                    }

                    res.ContentType = mime;

                    using (Stream inStream = remoteRes.GetResponseStream())
                    {
                        byte[] buffer = new byte[65536];
                        int bytesRead;
                        while ((bytesRead = inStream.Read(buffer, 0, buffer.Length)) > 0)
                        {
                            res.OutputStream.Write(buffer, 0, bytesRead);
                            res.OutputStream.Flush();
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                byte[] err = Encoding.UTF8.GetBytes("{\"error\":\"" + ex.Message.Replace("\"", "'") + "\"}");
                res.ContentType = "application/json";
                res.StatusCode = 502;
                try { res.OutputStream.Write(err, 0, err.Length); } catch {}
            }
            finally { try { res.Close(); } catch {} }
        }
    }
}
