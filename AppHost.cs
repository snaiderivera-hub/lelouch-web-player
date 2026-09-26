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
            string exeDir = AppDomain.CurrentDomain.BaseDirectory;
            string subDir = Path.Combine(exeDir, "iptv-app");
            appRoot = Directory.Exists(subDir) ? subDir : exeDir;

            webListener = BindListener(ref webPort, 8686, 8700);
            if (webListener == null) return;

            proxyListener = BindListener(ref proxyPort, 7878, 7890);
            if (proxyListener == null) return;

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

            Thread.Sleep(300);

            string appUrl = "http://localhost:" + webPort;

            string cmdLine = Environment.CommandLine;
            if (cmdLine.IndexOf("--open-browser", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                OpenPreferredBrowser(appUrl);
            }

            // Mantener la aplicación activa indefinidamente
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
