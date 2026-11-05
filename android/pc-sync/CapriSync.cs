// Capri Sync for Windows: the small program on the PC that Capri on Android talks to over the home Wi-Fi.
// It never changes CapriDesktop's program; it only reads and adds to the files Capri keeps in %LOCALAPPDATA%\Capri when
// the phone asks: her notes (me.md, or the Obsidian note picked in her settings), today's chat log, and her recent chat.
// C# 5 for .NET Framework 4.x, built with csc.exe like Capri (see build-sync.ps1). Run "CapriSync.exe --selftest" to check
// its lock against the phone's (the same test vector as core/src/test/.../Core2Test.kt).
//
// Fan-made companion to Capri (https://github.com/lachydotmcg/capri-desktop), PolyForm Noncommercial 1.0.0.

using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace CapriSync
{
    // The lock, written out the same way as SyncCrypto in the phone's core (Sync.kt).
    internal static class Lock
    {
        internal const int Iterations = 20000;
        internal const string Request = "capri-sync-v1 request";
        internal const string Reply = "capri-sync-v1 reply";
        private static readonly RandomNumberGenerator random = RandomNumberGenerator.Create();

        internal static string Code(string text)
        {
            StringBuilder b = new StringBuilder();
            foreach (char c in (text ?? "").ToUpperInvariant()) if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) b.Append(c);
            return b.ToString();
        }

        internal static byte[] Hmac(byte[] key, params byte[][] parts)
        {
            using (HMACSHA256 mac = new HMACSHA256(key))
            {
                foreach (byte[] p in parts) mac.TransformBlock(p, 0, p.Length, null, 0);
                mac.TransformFinalBlock(new byte[0], 0, 0);
                return mac.Hash;
            }
        }

        // One 32-byte PBKDF2-HMAC-SHA256 block.
        internal static byte[] Derive(string code, byte[] salt, int iterations)
        {
            byte[] password = Encoding.UTF8.GetBytes(Code(code));
            byte[] u = Hmac(password, salt, new byte[] { 0, 0, 0, 1 });
            byte[] t = (byte[])u.Clone();
            for (int i = 1; i < iterations; i++)
            {
                u = Hmac(password, u);
                for (int k = 0; k < t.Length; k++) t[k] ^= u[k];
            }
            return t;
        }

        internal static byte[] RandomBytes(int n) { byte[] b = new byte[n]; random.GetBytes(b); return b; }

        private static byte[] Concat(params byte[][] parts)
        {
            byte[] all = new byte[parts.Sum(p => p.Length)];
            int at = 0;
            foreach (byte[] p in parts) { Buffer.BlockCopy(p, 0, all, at, p.Length); at += p.Length; }
            return all;
        }

        private static SymmetricAlgorithm Aes(byte[] key, byte[] iv)
        {
            AesCryptoServiceProvider aes = new AesCryptoServiceProvider();
            aes.Mode = CipherMode.CBC;
            aes.Padding = PaddingMode.PKCS7;
            aes.Key = key;
            aes.IV = iv;
            return aes;
        }

        internal static string Seal(byte[] key, byte[] plain, string aad, byte[] iv)
        {
            byte[] enc = Hmac(key, Encoding.UTF8.GetBytes("capri-enc"));
            byte[] macKey = Hmac(key, Encoding.UTF8.GetBytes("capri-mac"));
            byte[] body;
            using (SymmetricAlgorithm aes = Aes(enc, iv)) using (ICryptoTransform t = aes.CreateEncryptor()) body = t.TransformFinalBlock(plain, 0, plain.Length);
            byte[] tag = Hmac(macKey, Encoding.UTF8.GetBytes(aad), iv, body);
            return Convert.ToBase64String(Concat(iv, body, tag));
        }

        internal static string Seal(byte[] key, string plain, string aad) { return Seal(key, Encoding.UTF8.GetBytes(plain), aad, RandomBytes(16)); }

        // The plain text, or null for a wrong key or a changed message.
        internal static string Open(byte[] key, string sealedText, string aad)
        {
            byte[] all;
            try { all = Convert.FromBase64String((sealedText ?? "").Trim()); }
            catch (FormatException) { return null; }
            if (all.Length < 16 + 16 + 32) return null;
            byte[] iv = new byte[16], body = new byte[all.Length - 48], tag = new byte[32];
            Buffer.BlockCopy(all, 0, iv, 0, 16);
            Buffer.BlockCopy(all, 16, body, 0, body.Length);
            Buffer.BlockCopy(all, all.Length - 32, tag, 0, 32);
            byte[] enc = Hmac(key, Encoding.UTF8.GetBytes("capri-enc"));
            byte[] macKey = Hmac(key, Encoding.UTF8.GetBytes("capri-mac"));
            byte[] want = Hmac(macKey, Encoding.UTF8.GetBytes(aad), iv, body);
            int diff = 0;
            for (int i = 0; i < 32; i++) diff |= want[i] ^ tag[i];
            if (diff != 0) return null;
            try
            {
                using (SymmetricAlgorithm aes = Aes(enc, iv)) using (ICryptoTransform t = aes.CreateDecryptor())
                    return Encoding.UTF8.GetString(t.TransformFinalBlock(body, 0, body.Length));
            }
            catch (CryptographicException) { return null; }
        }

        internal static string Hex(byte[] b) { StringBuilder s = new StringBuilder(); foreach (byte x in b) s.Append(x.ToString("x2")); return s.ToString(); }

        internal static byte[] Unhex(string h) { byte[] b = new byte[h.Length / 2]; for (int i = 0; i < b.Length; i++) b[i] = Convert.ToByte(h.Substring(i * 2, 2), 16); return b; }
    }

    // A phone that paired: its id, its name and its key (kept encrypted for this Windows user with DPAPI).
    internal sealed class Phone
    {
        internal string Id, Name;
        internal byte[] Key;
        internal DateTime Paired;
    }

    // Where Capri keeps her things on this PC, the same paths CapriDesktop uses (app/Capri.cs).
    internal static class Places
    {
        internal static string Capri { get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Capri"); } }
        internal static string Own { get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CapriSync"); } }
        internal static string Transcripts { get { return Path.Combine(Capri, "transcripts"); } }
        internal static string Received { get { return Path.Combine(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads"), "Capri Sync"); } }

        // Her notes file: the note picked as her Obsidian vault in settings.json, or me.md in her folder.
        internal static string Notes
        {
            get
            {
                try
                {
                    string settings = Path.Combine(Capri, "settings.json");
                    if (File.Exists(settings))
                    {
                        Dictionary<string, object> s = Json.Read(File.ReadAllText(settings));
                        object v;
                        if (s != null && s.TryGetValue("Vault", out v) && v is string && ((string)v).Length > 0)
                        {
                            string chosen = (string)v;
                            string note = chosen.EndsWith(".md", StringComparison.OrdinalIgnoreCase) ? chosen : Path.Combine(chosen, "me.md");
                            if (Directory.Exists(Path.GetDirectoryName(note))) return note;
                        }
                    }
                }
                catch (Exception) { }
                return Path.Combine(Capri, "me.md");
            }
        }
    }

    internal static class Json
    {
        private static JavaScriptSerializer Make() { JavaScriptSerializer j = new JavaScriptSerializer(); j.MaxJsonLength = int.MaxValue; j.RecursionLimit = 64; return j; }
        internal static Dictionary<string, object> Read(string text) { try { return Make().Deserialize<Dictionary<string, object>>(text); } catch (Exception) { return null; } }
        internal static string Write(object value) { return Make().Serialize(value); }
        internal static string Str(Dictionary<string, object> o, string key) { object v; return o != null && o.TryGetValue(key, out v) && v != null ? Convert.ToString(v, System.Globalization.CultureInfo.InvariantCulture) : null; }
        internal static Dictionary<string, object> Obj(Dictionary<string, object> o, string key) { object v; return o != null && o.TryGetValue(key, out v) ? v as Dictionary<string, object> : null; }
        internal static List<object> Arr(Dictionary<string, object> o, string key)
        {
            object v;
            if (o == null || !o.TryGetValue(key, out v) || v == null) return new List<object>();
            System.Collections.IEnumerable e = v as System.Collections.IEnumerable;
            if (e == null || v is string) return new List<object>();
            return e.Cast<object>().ToList();
        }
        internal static long Long(Dictionary<string, object> o, string key) { object v; if (o == null || !o.TryGetValue(key, out v) || v == null) return 0; try { return Convert.ToInt64(v, System.Globalization.CultureInfo.InvariantCulture); } catch (Exception) { return 0; } }
    }

    // The same merge as SyncMerge.notes on the phone: this side's head and lines in order, then the other side's new lines.
    internal static class Merge
    {
        internal const string NotesHead = "# What Capri remembers about you\n> She reads this every time you chat. Edit or delete anything here.";

        internal static string Notes(string mine, string theirs)
        {
            string[] mineLines = (mine ?? "").Replace("\r\n", "\n").Split('\n');
            List<string> head = new List<string>();
            foreach (string l in mineLines) { if (l.Trim().StartsWith("- ", StringComparison.Ordinal)) break; head.Add(l); }
            string headText = string.Join("\n", head.ToArray()).TrimEnd();
            if (headText.Trim().Length == 0) headText = NotesHead;
            HashSet<string> seen = new HashSet<string>();
            List<string> outLines = new List<string>();
            foreach (string l in mineLines.Concat((theirs ?? "").Replace("\r\n", "\n").Split('\n')))
            {
                string t = l.Trim();
                if (!t.StartsWith("- ", StringComparison.Ordinal)) continue;
                if (seen.Add(t.ToLowerInvariant())) outLines.Add(t);
            }
            return headText + "\n\n" + string.Join("\n", outLines.ToArray()) + "\n";
        }

        private static readonly Regex Line = new Regex(@"^\*\*(\d\d:\d\d) ([^*]+?):\*\* (.*)$");

        internal static string Tagged(string line, string from)
        {
            Match m = Line.Match(line ?? "");
            if (!m.Success || m.Groups[2].Value.Contains("(")) return null;
            return "**" + m.Groups[1].Value + " " + m.Groups[2].Value + " (" + from + "):** " + m.Groups[3].Value;
        }
    }

    internal sealed class Handoff
    {
        internal DateTime At;
        internal string Phone, Text;
        public override string ToString() { return At.ToString("HH:mm") + "  " + Phone + " — " + Text.Replace("\n", " ").Substring(0, Math.Min(80, Text.Replace("\n", " ").Length)); }
    }

    // The server: plain HTTP/1.1 on port 47917, answering only addresses on their own network, every body sealed.
    internal sealed class Server
    {
        internal const int Port = 47917;
        private readonly Hub hub;
        private TcpListener listener;
        private volatile bool running;

        internal Server(Hub hub) { this.hub = hub; }

        internal void Start()
        {
            listener = new TcpListener(IPAddress.Any, Port);
            listener.Start();
            running = true;
            Thread t = new Thread(Accept);
            t.IsBackground = true;
            t.Start();
        }

        internal void Stop() { running = false; try { listener.Stop(); } catch (Exception) { } }

        private void Accept()
        {
            while (running)
            {
                TcpClient c;
                try { c = listener.AcceptTcpClient(); }
                catch (Exception) { if (!running) return; continue; }
                ThreadPool.QueueUserWorkItem(delegate { Serve(c); });
            }
        }

        internal static bool Private(IPAddress a)
        {
            if (IPAddress.IsLoopback(a)) return true;
            if (a.IsIPv4MappedToIPv6) a = a.MapToIPv4();
            byte[] b = a.GetAddressBytes();
            if (b.Length == 4) return b[0] == 10 || (b[0] == 172 && b[1] >= 16 && b[1] <= 31) || (b[0] == 192 && b[1] == 168) || (b[0] == 169 && b[1] == 254) || (b[0] == 100 && b[1] >= 64 && b[1] <= 127);
            return a.IsIPv6LinkLocal || a.IsIPv6SiteLocal || (b[0] & 0xfe) == 0xfc;
        }

        private static string ReadLine(Stream s)
        {
            StringBuilder b = new StringBuilder();
            int c;
            while ((c = s.ReadByte()) >= 0) { if (c == '\n') break; if (c != '\r') b.Append((char)c); if (b.Length > 8192) return null; }
            return c < 0 && b.Length == 0 ? null : b.ToString();
        }

        private void Serve(TcpClient c)
        {
            using (c)
            {
                try
                {
                    c.ReceiveTimeout = 20000; c.SendTimeout = 20000;
                    IPEndPoint from = (IPEndPoint)c.Client.RemoteEndPoint;
                    NetworkStream s = c.GetStream();
                    if (!Private(from.Address)) { Send(s, 403, ""); return; }
                    string first = ReadLine(s);
                    if (first == null) return;
                    string[] parts = first.Split(' ');
                    if (parts.Length < 2) { Send(s, 400, ""); return; }
                    int length = 0;
                    string line;
                    while ((line = ReadLine(s)) != null && line.Length > 0)
                    {
                        int colon = line.IndexOf(':');
                        if (colon > 0 && line.Substring(0, colon).Trim().Equals("Content-Length", StringComparison.OrdinalIgnoreCase)) int.TryParse(line.Substring(colon + 1).Trim(), out length);
                    }
                    if (length < 0 || length > 40 * 1024 * 1024) { Send(s, 413, ""); return; }
                    byte[] body = new byte[length];
                    int got = 0;
                    while (got < length) { int n = s.Read(body, got, length - got); if (n <= 0) break; got += n; }
                    string text = Encoding.UTF8.GetString(body, 0, got);
                    int status;
                    string reply = hub.Handle(parts[0], parts[1], text, from.Address.ToString(), out status);
                    Send(s, status, reply);
                }
                catch (Exception e) { hub.Log("Connexion : " + e.Message); }
            }
        }

        private static void Send(Stream s, int status, string body)
        {
            byte[] b = Encoding.UTF8.GetBytes(body ?? "");
            string reason = status == 200 ? "OK" : status == 401 ? "Unauthorized" : status == 403 ? "Forbidden" : status == 404 ? "Not Found" : status == 413 ? "Too Large" : "Error";
            byte[] head = Encoding.ASCII.GetBytes("HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: " + (status == 200 && body != null && body.StartsWith("{") ? "application/json" : "text/plain") + "; charset=utf-8\r\nContent-Length: " + b.Length + "\r\nConnection: close\r\n\r\n");
            s.Write(head, 0, head.Length);
            s.Write(b, 0, b.Length);
            s.Flush();
        }
    }

    // What the phone can ask for, and the state behind it: the pairing code, the paired phones, handed-off chats.
    internal sealed class Hub
    {
        internal event Action Changed;
        internal event Action<Handoff> HandedOff;
        private readonly object gate = new object();
        private readonly List<Phone> phones = new List<Phone>();
        private readonly Dictionary<string, DateTime> recent = new Dictionary<string, DateTime>();
        private readonly List<string> log = new List<string>();
        internal readonly List<Handoff> Handoffs = new List<Handoff>();
        internal string PairCode { get; private set; }
        private byte[] salt;
        private DateTime codeUntil;
        private int misses;

        private string PhonesFile { get { return Path.Combine(Places.Own, "phones.dat"); } }

        internal Hub() { Directory.CreateDirectory(Places.Own); Load(); NewCode(); }

        internal void Log(string what) { lock (log) { log.Add(DateTime.Now.ToString("HH:mm:ss") + "  " + what); while (log.Count > 200) log.RemoveAt(0); } Fire(); }
        internal string[] LogLines() { lock (log) return log.ToArray(); }
        private void Fire() { Action a = Changed; if (a != null) a(); }

        // Eight letters and digits without the ones people mix up, valid five minutes, five tries.
        internal void NewCode()
        {
            const string letters = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
            byte[] r = Lock.RandomBytes(8);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 8; i++) { if (i == 4) b.Append('-'); b.Append(letters[r[i] % letters.Length]); }
            lock (gate) { PairCode = b.ToString(); salt = Lock.RandomBytes(16); codeUntil = DateTime.UtcNow.AddMinutes(5); misses = 0; }
            Fire();
        }

        internal bool CodeLive { get { return DateTime.UtcNow < codeUntil && misses < 5; } }
        internal Phone[] Phones { get { lock (gate) return phones.ToArray(); } }

        internal void Forget(Phone p) { lock (gate) phones.RemoveAll(x => x.Id == p.Id); Save(); Log("Téléphone oublié : " + p.Name); }

        private void Load()
        {
            try
            {
                if (!File.Exists(PhonesFile)) return;
                byte[] plain = ProtectedData.Unprotect(File.ReadAllBytes(PhonesFile), Encoding.UTF8.GetBytes("Capri Sync"), DataProtectionScope.CurrentUser);
                Dictionary<string, object> root = Json.Read(Encoding.UTF8.GetString(plain));
                foreach (object o in Json.Arr(root, "phones"))
                {
                    Dictionary<string, object> d = o as Dictionary<string, object>;
                    if (d == null) continue;
                    phones.Add(new Phone { Id = Json.Str(d, "id"), Name = Json.Str(d, "name"), Key = Convert.FromBase64String(Json.Str(d, "key")), Paired = DateTime.FromBinary(Json.Long(d, "paired")) });
                }
            }
            catch (Exception e) { Log("Liste des téléphones illisible, il faudra refaire l'appairage (" + e.Message + ")."); }
        }

        private void Save()
        {
            List<object> list = new List<object>();
            lock (gate) foreach (Phone p in phones) list.Add(new Dictionary<string, object> { { "id", p.Id }, { "name", p.Name }, { "key", Convert.ToBase64String(p.Key) }, { "paired", p.Paired.ToBinary() } });
            byte[] plain = Encoding.UTF8.GetBytes(Json.Write(new Dictionary<string, object> { { "phones", list } }));
            File.WriteAllBytes(PhonesFile, ProtectedData.Protect(plain, Encoding.UTF8.GetBytes("Capri Sync"), DataProtectionScope.CurrentUser));
        }

        private static long Now() { return (long)(DateTime.UtcNow - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalMilliseconds; }

        private static bool Fresh(Dictionary<string, object> msg) { return Math.Abs(Now() - Json.Long(msg, "ts")) < 10 * 60 * 1000L; }

        private string Answer(byte[] key, object data, string error)
        {
            Dictionary<string, object> a = new Dictionary<string, object> { { "ok", error == null }, { "ts", Now() } };
            if (error != null) a["error"] = error; else a["data"] = data ?? new Dictionary<string, object>();
            return Lock.Seal(key, Json.Write(a), Lock.Reply);
        }

        internal string Handle(string method, string path, string body, string from, out int status)
        {
            status = 200;
            if (method == "GET" && path == "/hello")
            {
                string s;
                lock (gate) s = Lock.Hex(salt);
                return Json.Write(new Dictionary<string, object> { { "app", "Capri Sync" }, { "version", 1 }, { "salt", s }, { "name", Environment.MachineName } });
            }
            if (method != "POST") { status = 404; return ""; }
            if (path == "/pair") return Pair(body, from, out status);
            if (path == "/api") return Api(body, out status);
            status = 404;
            return "";
        }

        private string Pair(string body, string from, out int status)
        {
            status = 200;
            byte[] codeKey;
            lock (gate)
            {
                if (!CodeLive) { status = 403; Log("Appairage refusé depuis " + from + " : le code a expiré, clique sur Nouveau code."); return ""; }
                codeKey = Lock.Derive(PairCode, salt, Lock.Iterations);
            }
            string plain = Lock.Open(codeKey, body, Lock.Request);
            Dictionary<string, object> msg = plain == null ? null : Json.Read(plain);
            if (msg == null || Json.Str(msg, "op") != "pair" || !Fresh(msg) || string.IsNullOrEmpty(Json.Str(msg, "id")))
            {
                lock (gate) misses++;
                status = 403;
                Log("Mauvais code d'appairage depuis " + from + ".");
                Fire();
                return "";
            }
            Phone p = new Phone { Id = Json.Str(msg, "id"), Name = Json.Str(Json.Obj(msg, "data"), "phone") ?? "Téléphone", Key = Lock.RandomBytes(32), Paired = DateTime.Now };
            lock (gate) { phones.RemoveAll(x => x.Id == p.Id); phones.Add(p); codeUntil = DateTime.MinValue; }
            Save();
            Log("Appairé avec " + p.Name + " (" + from + ").");
            NewCode();
            return Answer(codeKey, new Dictionary<string, object> { { "key", Convert.ToBase64String(p.Key) }, { "pc", Environment.MachineName } }, null);
        }

        private string Api(string body, out int status)
        {
            status = 200;
            Phone phone = null;
            Dictionary<string, object> msg = null;
            foreach (Phone p in Phones)
            {
                string plain = Lock.Open(p.Key, body, Lock.Request);
                if (plain == null) continue;
                phone = p;
                msg = Json.Read(plain);
                break;
            }
            if (phone == null || msg == null) { status = 401; return ""; }
            string hash = Lock.Hex(SHA256.Create().ComputeHash(Encoding.UTF8.GetBytes(body)));
            lock (recent)
            {
                foreach (string old in recent.Where(kv => DateTime.UtcNow - kv.Value > TimeSpan.FromMinutes(20)).Select(kv => kv.Key).ToList()) recent.Remove(old);
                if (recent.ContainsKey(hash) || !Fresh(msg) || Json.Str(msg, "id") != phone.Id) return Answer(phone.Key, null, "replayed or stale");
                recent[hash] = DateTime.UtcNow;
            }
            string op = Json.Str(msg, "op");
            Dictionary<string, object> data = Json.Obj(msg, "data") ?? new Dictionary<string, object>();
            try
            {
                switch (op)
                {
                    case "ping": return Answer(phone.Key, new Dictionary<string, object> { { "pc", Environment.MachineName }, { "version", "1.0" } }, null);
                    case "handoff": return Answer(phone.Key, TakeHandoff(phone, data), null);
                    case "pull_chat": return Answer(phone.Key, PullChat(), null);
                    case "notes": return Answer(phone.Key, Notes(phone, data), null);
                    case "projects": return Answer(phone.Key, Projects(data), null);
                    case "transcript": return Answer(phone.Key, Transcript(phone, data), null);
                    case "file": return Answer(phone.Key, Receive(phone, data), null);
                    case "pull_files": return Answer(phone.Key, PullFiles(phone), null);
                    default: return Answer(phone.Key, null, "unknown op");
                }
            }
            catch (Exception e) { Log(op + " : " + e.Message); return Answer(phone.Key, null, e.Message); }
        }

        private object TakeHandoff(Phone phone, Dictionary<string, object> data)
        {
            StringBuilder b = new StringBuilder();
            foreach (object o in Json.Arr(data, "turns"))
            {
                Dictionary<string, object> t = o as Dictionary<string, object>;
                if (t == null) continue;
                b.Append(Json.Str(t, "role") == "user" ? "Moi : " : "Capri : ").Append(Json.Str(t, "text")).Append('\n');
            }
            Handoff h = new Handoff { At = DateTime.Now, Phone = phone.Name, Text = b.ToString().Trim() };
            lock (Handoffs) { Handoffs.Insert(0, h); while (Handoffs.Count > 20) Handoffs.RemoveAt(Handoffs.Count - 1); }
            string dir = Path.Combine(Places.Own, "handoffs");
            Directory.CreateDirectory(dir);
            File.WriteAllText(Path.Combine(dir, h.At.ToString("yyyy-MM-dd_HHmmss") + ".md"), "# Conversation reprise du téléphone (" + phone.Name + ")\n\n" + h.Text + "\n", Encoding.UTF8);
            Log("Conversation reçue de " + phone.Name + ".");
            Action<Handoff> a = HandedOff;
            if (a != null) a(h);
            return new Dictionary<string, object> { { "shown", true } };
        }

        // Her recent chat on the PC, read from CapriDesktop's chat.json without touching it.
        private object PullChat()
        {
            List<object> turns = new List<object>();
            string path = Path.Combine(Places.Capri, "chat.json");
            if (File.Exists(path))
            {
                Dictionary<string, object> root = Json.Read(ReadShared(path));
                foreach (object o in Json.Arr(root, "messages"))
                {
                    Dictionary<string, object> m = o as Dictionary<string, object>;
                    if (m == null) continue;
                    string role = Json.Str(m, "role"), text = Json.Str(m, "content");
                    if ((role == "user" || role == "assistant") && !string.IsNullOrEmpty(text)) turns.Add(new Dictionary<string, object> { { "role", role }, { "text", text.Length > 2000 ? text.Substring(0, 2000) : text } });
                }
            }
            return new Dictionary<string, object> { { "turns", turns.Skip(Math.Max(0, turns.Count - 12)).ToList() } };
        }

        private static string ReadShared(string path)
        {
            using (FileStream f = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete))
            using (StreamReader r = new StreamReader(f, Encoding.UTF8)) return r.ReadToEnd();
        }

        private static void WriteSafely(string path, string text)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path));
            string temp = path + ".capri-sync.tmp";
            File.WriteAllText(temp, text, new UTF8Encoding(false));
            if (File.Exists(path)) File.Replace(temp, path, null); else File.Move(temp, path);
        }

        // me.md both ways: the PC keeps its lines and order and gains the phone's new ones; the first time, a backup.
        private object Notes(Phone phone, Dictionary<string, object> data)
        {
            string path = Places.Notes;
            string mine = File.Exists(path) ? ReadShared(path) : "";
            string merged = Merge.Notes(mine, Json.Str(data, "notes") ?? "");
            if (merged.Replace("\r\n", "\n").Trim() != mine.Replace("\r\n", "\n").Trim())
            {
                string backup = path + ".capri-sync-backup";
                if (File.Exists(path) && !File.Exists(backup)) File.Copy(path, backup);
                WriteSafely(path, merged);
                Log("Notes de Capri mises à jour depuis " + phone.Name + ".");
            }
            return new Dictionary<string, object> { { "notes", merged } };
        }

        // Project memories, the newest copy of each kept here and sent back.
        private object Projects(Dictionary<string, object> data)
        {
            string dir = Path.Combine(Places.Own, "projects");
            Directory.CreateDirectory(dir);
            Regex safe = new Regex(@"^[\w .-]{1,80}\.md$");
            foreach (object o in Json.Arr(data, "projects"))
            {
                Dictionary<string, object> p = o as Dictionary<string, object>;
                string file = Json.Str(p, "file");
                if (file == null || !safe.IsMatch(file)) continue;
                long at = Json.Long(p, "at");
                string path = Path.Combine(dir, file);
                DateTime when = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc).AddMilliseconds(at);
                if (!File.Exists(path) || File.GetLastWriteTimeUtc(path) < when) { File.WriteAllText(path, Json.Str(p, "text") ?? "", new UTF8Encoding(false)); File.SetLastWriteTimeUtc(path, when); }
            }
            List<object> all = new List<object>();
            foreach (string f in Directory.GetFiles(dir, "*.md"))
                all.Add(new Dictionary<string, object> { { "file", Path.GetFileName(f) }, { "text", File.ReadAllText(f, Encoding.UTF8) }, { "at", (long)(File.GetLastWriteTimeUtc(f) - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalMilliseconds } });
            return new Dictionary<string, object> { { "projects", all } };
        }

        // Today's lines from the phone go into her PC chat log, tagged "(téléphone)", so her recall on the PC finds them.
        private object Transcript(Phone phone, Dictionary<string, object> data)
        {
            string day = Json.Str(data, "day") ?? DateTime.Now.ToString("yyyy-MM-dd");
            if (!Regex.IsMatch(day, @"^\d{4}-\d{2}-\d{2}$")) throw new InvalidDataException("bad day");
            string path = Path.Combine(Places.Transcripts, day + ".md");
            Directory.CreateDirectory(Places.Transcripts);
            string have = File.Exists(path) ? ReadShared(path) : "";
            StringBuilder add = new StringBuilder();
            int n = 0;
            foreach (object o in Json.Arr(data, "lines"))
            {
                string tagged = Merge.Tagged(o as string, "téléphone");
                if (tagged == null || have.Contains(tagged)) continue;
                add.Append(tagged).Append('\n');
                n++;
            }
            if (n > 0)
            {
                if (have.Length == 0)
                {
                    DateTime d = DateTime.ParseExact(day, "yyyy-MM-dd", System.Globalization.CultureInfo.InvariantCulture);
                    add.Insert(0, "# Capri and you, " + d.ToString("dddd d MMMM yyyy", System.Globalization.CultureInfo.InvariantCulture) + "\n> Kept on this PC only, never uploaded. Delete a line or the whole file whenever you like.\n\n");
                }
                File.AppendAllText(path, add.ToString(), new UTF8Encoding(false));
                Log(n + " ligne(s) de discussion reçues de " + phone.Name + ".");
            }
            return new Dictionary<string, object> { { "added", n } };
        }

        // Files for the phone: dropped on the window or picked with "Vers le téléphone…", waiting in CapriSync\outbox.
        internal string Outbox { get { return Path.Combine(Places.Own, "outbox"); } }

        internal int Waiting { get { return Directory.Exists(Outbox) ? Directory.GetFiles(Outbox).Length : 0; } }

        internal int Queue(string[] paths)
        {
            Directory.CreateDirectory(Outbox);
            int n = 0;
            foreach (string path in paths)
            {
                try
                {
                    FileInfo f = new FileInfo(path);
                    if (!f.Exists || f.Length > 20L * 1024 * 1024) { Log("Introuvable ou trop gros (20 Mo max) : " + f.Name); continue; }
                    string to = Path.Combine(Outbox, f.Name);
                    for (int i = 2; File.Exists(to); i++) to = Path.Combine(Outbox, Path.GetFileNameWithoutExtension(f.Name) + " (" + i + ")" + f.Extension);
                    File.Copy(path, to);
                    n++;
                }
                catch (Exception e) { Log("Fichier non ajouté : " + e.Message); }
            }
            if (n > 0) Log(n + " fichier(s) prêt(s) pour le téléphone. Sur le téléphone, dis « récupère les fichiers du PC ».");
            return n;
        }

        // Up to about 24 MB per answer; the phone asks again for the rest. Sent files move to CapriSync\sent, never deleted.
        private object PullFiles(Phone phone)
        {
            List<object> files = new List<object>();
            string[] all = Directory.Exists(Outbox) ? Directory.GetFiles(Outbox).OrderBy(x => File.GetCreationTimeUtc(x)).ToArray() : new string[0];
            string sentDir = Path.Combine(Places.Own, "sent");
            Directory.CreateDirectory(sentDir);
            long total = 0;
            int sent = 0;
            foreach (string f in all)
            {
                long size = new FileInfo(f).Length;
                if (files.Count > 0 && total + size > 24L * 1024 * 1024) break;
                files.Add(new Dictionary<string, object> { { "name", Path.GetFileName(f) }, { "data", Convert.ToBase64String(File.ReadAllBytes(f)) } });
                total += size;
                string to = Path.Combine(sentDir, DateTime.Now.ToString("yyyyMMdd-HHmmss") + " " + Path.GetFileName(f));
                for (int i = 2; File.Exists(to); i++) to = Path.Combine(sentDir, DateTime.Now.ToString("yyyyMMdd-HHmmss") + " (" + i + ") " + Path.GetFileName(f));
                File.Move(f, to);
                sent++;
            }
            if (sent > 0) Log(sent + " fichier(s) envoyé(s) à " + phone.Name + ".");
            return new Dictionary<string, object> { { "files", files }, { "left", all.Length - sent } };
        }

        private object Receive(Phone phone, Dictionary<string, object> data)
        {
            string name = Path.GetFileName(Json.Str(data, "name") ?? "fichier");
            foreach (char c in Path.GetInvalidFileNameChars()) name = name.Replace(c, '_');
            if (name.Trim('.', ' ').Length == 0) name = "fichier";
            byte[] bytes = Convert.FromBase64String(Json.Str(data, "data") ?? "");
            Directory.CreateDirectory(Places.Received);
            string path = Path.Combine(Places.Received, name);
            for (int i = 2; File.Exists(path); i++) path = Path.Combine(Places.Received, Path.GetFileNameWithoutExtension(name) + " (" + i + ")" + Path.GetExtension(name));
            File.WriteAllBytes(path, bytes);
            Log("Fichier reçu de " + phone.Name + " : " + Path.GetFileName(path));
            return new Dictionary<string, object> { { "saved", "Téléchargements\\Capri Sync\\" + Path.GetFileName(path) } };
        }
    }

    internal sealed class Window : Form
    {
        private readonly Hub hub;
        private readonly Label code = new Label(), address = new Label(), status = new Label();
        private readonly ListBox phones = new ListBox(), handoffs = new ListBox(), log = new ListBox();
        private readonly NotifyIcon tray = new NotifyIcon();
        private readonly System.Windows.Forms.Timer tick = new System.Windows.Forms.Timer();
        private static readonly Color CGround = Color.FromArgb(10, 10, 16), CField = Color.FromArgb(17, 16, 28), CPink = Color.FromArgb(255, 42, 109), CCyan = Color.FromArgb(94, 240, 246), CText = Color.FromArgb(236, 236, 241), CFaint = Color.FromArgb(146, 130, 140);

        internal Window(Hub hub)
        {
            this.hub = hub;
            base.Text = "Capri Sync";
            ClientSize = new Size(560, 600);
            BackColor = CGround; ForeColor = CText;
            Font = new Font("Consolas", 10f);
            FormBorderStyle = FormBorderStyle.FixedSingle; MaximizeBox = false;
            Controls.Add(Title("CAPRI SYNC", 16, 12, 20f, CPink));
            Controls.Add(Hint("Laisse cette fenêtre ouverte. Ton téléphone et ce PC doivent être sur le même Wi-Fi. Si Windows le demande, autorise Capri Sync sur les réseaux privés seulement.", 16, 48, 528, 36));
            Controls.Add(Title("ADRESSE DU PC", 16, 92, 10f, CCyan));
            address.SetBounds(16, 112, 528, 22); address.ForeColor = CText; Controls.Add(address);
            Controls.Add(Title("CODE D'APPAIRAGE", 16, 142, 10f, CCyan));
            code.SetBounds(16, 162, 300, 40); code.Font = new Font("Consolas", 24f, FontStyle.Bold); code.ForeColor = CPink; Controls.Add(code);
            Button again = MakeButton("Nouveau code", 380, 168, 164); again.Click += delegate { hub.NewCode(); Redraw(); }; Controls.Add(again);
            status.SetBounds(16, 204, 528, 20); status.ForeColor = CFaint; Controls.Add(status);
            Controls.Add(Title("TÉLÉPHONES APPAIRÉS", 16, 232, 10f, CCyan));
            Style(phones); phones.SetBounds(16, 252, 380, 60); Controls.Add(phones);
            Button forget = MakeButton("Oublier", 404, 252, 140); forget.Click += delegate { int i = phones.SelectedIndex; Phone[] all = hub.Phones; if (i >= 0 && i < all.Length) { hub.Forget(all[i]); Redraw(); } }; Controls.Add(forget);
            Controls.Add(Title("CONVERSATIONS REÇUES", 16, 322, 10f, CCyan));
            Style(handoffs); handoffs.SetBounds(16, 342, 380, 90); Controls.Add(handoffs);
            Button copy = MakeButton("Copier pour Capri", 404, 342, 140); copy.Click += delegate { CopyHandoff(); }; Controls.Add(copy);
            Button files = MakeButton("Fichiers reçus", 404, 382, 140); files.Click += delegate { Directory.CreateDirectory(Places.Received); System.Diagnostics.Process.Start("explorer.exe", "\"" + Places.Received + "\""); }; Controls.Add(files);
            Button toPhone = MakeButton("Vers le téléphone…", 404, 422, 140);
            toPhone.Click += delegate
            {
                using (OpenFileDialog pick = new OpenFileDialog())
                {
                    pick.Title = "Fichiers à envoyer au téléphone (20 Mo max chacun)";
                    pick.Multiselect = true;
                    if (pick.ShowDialog(this) == DialogResult.OK) hub.Queue(pick.FileNames);
                }
            };
            Controls.Add(toPhone);
            // Files dropped on the window go to the phone too.
            AllowDrop = true;
            DragEnter += delegate(object sender, DragEventArgs e) { if (e.Data.GetDataPresent(DataFormats.FileDrop)) e.Effect = DragDropEffects.Copy; };
            DragDrop += delegate(object sender, DragEventArgs e) { string[] dropped = e.Data.GetData(DataFormats.FileDrop) as string[]; if (dropped != null) hub.Queue(dropped); };
            Controls.Add(Title("JOURNAL", 16, 442, 10f, CCyan));
            Style(log); log.SetBounds(16, 462, 528, 120); Controls.Add(log);
            tray.Text = "Capri Sync";
            tray.Icon = SystemIcons.Application;
            tray.Visible = true;
            tray.DoubleClick += delegate { Show(); WindowState = FormWindowState.Normal; Activate(); };
            hub.Changed += delegate { if (IsHandleCreated) BeginInvoke(new Action(Redraw)); };
            hub.HandedOff += delegate(Handoff h) { if (IsHandleCreated) BeginInvoke(new Action(delegate { tray.ShowBalloonTip(5000, "Capri Sync", "Conversation reçue de " + h.Phone + ". Ouvre Capri Sync pour la copier dans Capri.", ToolTipIcon.Info); Redraw(); })); };
            tick.Interval = 1000; tick.Tick += delegate { RefreshCode(); }; tick.Start();
            Redraw();
        }

        private static Label Title(string text, int x, int y, float size, Color color) { Label l = new Label(); l.Text = text; l.Font = new Font("Consolas", size, FontStyle.Bold); l.ForeColor = color; l.AutoSize = true; l.Location = new Point(x, y); return l; }
        private static Label Hint(string text, int x, int y, int w, int h) { Label l = new Label(); l.Text = text; l.ForeColor = CFaint; l.SetBounds(x, y, w, h); return l; }
        private static Button MakeButton(string text, int x, int y, int w) { Button b = new Button(); b.Text = text; b.SetBounds(x, y, w, 30); b.FlatStyle = FlatStyle.Flat; b.FlatAppearance.BorderColor = CCyan; b.ForeColor = CCyan; b.BackColor = CField; return b; }
        private static void Style(ListBox l) { l.BackColor = CField; l.ForeColor = CText; l.BorderStyle = BorderStyle.FixedSingle; l.IntegralHeight = false; }

        private static IEnumerable<string> Addresses()
        {
            foreach (NetworkInterface n in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (n.OperationalStatus != OperationalStatus.Up || n.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                foreach (UnicastIPAddressInformation a in n.GetIPProperties().UnicastAddresses)
                    if (a.Address.AddressFamily == AddressFamily.InterNetwork && Server.Private(a.Address)) yield return a.Address.ToString();
            }
        }

        private void RefreshCode()
        {
            code.Text = hub.CodeLive ? hub.PairCode : "— expiré —";
            status.Text = hub.CodeLive ? "Valable 5 minutes, une seule fois. Entre-le dans Capri > Réglages > SYNCHRO PC." : "Clique sur Nouveau code pour appairer un téléphone.";
        }

        private void Redraw()
        {
            address.Text = string.Join("   ", Addresses().ToArray()) + "   (port " + Server.Port + ")";
            RefreshCode();
            phones.Items.Clear();
            foreach (Phone p in hub.Phones) phones.Items.Add(p.Name + "  ·  appairé le " + p.Paired.ToString("dd/MM HH:mm"));
            handoffs.Items.Clear();
            lock (hub.Handoffs) foreach (Handoff h in hub.Handoffs) handoffs.Items.Add(h);
            log.Items.Clear();
            foreach (string l in hub.LogLines().Reverse()) log.Items.Add(l);
        }

        // The conversation from the phone, ready to paste into Capri on the PC: she carries on from there.
        private void CopyHandoff()
        {
            Handoff h = handoffs.SelectedItem as Handoff;
            if (h == null) lock (hub.Handoffs) h = hub.Handoffs.FirstOrDefault();
            if (h == null) return;
            Clipboard.SetText("On reprend ce qu'on faisait sur mon téléphone :\n" + h.Text + "\n\nOn continue d'ici.");
            status.Text = "Copié : colle-le dans la conversation avec Capri.";
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (e.CloseReason == CloseReason.UserClosing && MessageBox.Show("Arrêter Capri Sync ? Ton téléphone ne pourra plus se synchroniser.", "Capri Sync", MessageBoxButtons.YesNo) != DialogResult.Yes) { e.Cancel = true; return; }
            tray.Visible = false;
            base.OnFormClosing(e);
        }
    }

    internal static class Program
    {
        // The same vector as Core2Test.syncLockIsStandardPbkdf2AndCatchesChanges on the phone side.
        private const string VectorKey = "4656ab988baa6eb5c08017ddacbbf5594fc0c80fd4e7f7d10841261ee6d51553";
        private const string VectorSealed = "EBESExQVFhcYGRobHB0eH3COnKeOwN58MSiM7BwYyrvf7dpgfabByRC5Td4s6+EEB6AL24em0WIkq9tOSBNIAw==";

        private static int SelfTest()
        {
            int failures = 0;
            Action<string, bool> check = delegate(string what, bool ok) { Console.WriteLine((ok ? "PASS " : "FAIL ") + what); if (!ok) failures++; };
            byte[] salt = new byte[16];
            for (int i = 0; i < 16; i++) salt[i] = (byte)i;
            byte[] key = Lock.Derive("k7q2-m9xa", salt, Lock.Iterations);
            check("PBKDF2 key matches the phone", Lock.Hex(key) == VectorKey);
            byte[] iv = new byte[16];
            for (int i = 0; i < 16; i++) iv[i] = (byte)(16 + i);
            check("sealed message matches the phone", Lock.Seal(key, Encoding.UTF8.GetBytes("{\"op\":\"hello\"}"), Lock.Request, iv) == VectorSealed);
            check("opens the phone's message", Lock.Open(key, VectorSealed, Lock.Request) == "{\"op\":\"hello\"}");
            check("a reply can't pass for a request", Lock.Open(key, VectorSealed, Lock.Reply) == null);
            byte[] changed = Convert.FromBase64String(VectorSealed);
            changed[20] ^= 1;
            check("a changed message is refused", Lock.Open(key, Convert.ToBase64String(changed), Lock.Request) == null);
            check("notes merge like the phone", Merge.Notes("# What Capri remembers about you\n> head\n\n- A\n- B\n", "# other head\n- b\n- C\n") == "# What Capri remembers about you\n> head\n\n- A\n- B\n- C\n");
            check("a transcript line is tagged once", Merge.Tagged("**14:02 You:** salut", "téléphone") == "**14:02 You (téléphone):** salut" && Merge.Tagged("**14:02 You (PC):** salut", "téléphone") == null);
            check("private addresses only", Server.Private(IPAddress.Parse("192.168.1.20")) && Server.Private(IPAddress.Parse("10.0.0.5")) && !Server.Private(IPAddress.Parse("8.8.8.8")));
            check("JSON round trip", Json.Str(Json.Read(Json.Write(new Dictionary<string, object> { { "ok", true }, { "ts", 1730000000000L }, { "t", "é\"\n" } })), "t") == "é\"\n");
            Console.WriteLine(failures == 0 ? "All good." : failures + " check(s) failed.");
            return failures == 0 ? 0 : 1;
        }

        [STAThread]
        private static int Main(string[] args)
        {
            if (args.Length > 0 && args[0] == "--selftest") return SelfTest();
            bool first;
            using (Mutex one = new Mutex(true, "CapriSync.SingleInstance", out first))
            {
                if (!first) { MessageBox.Show("Capri Sync est déjà ouvert.", "Capri Sync"); return 0; }
                Application.EnableVisualStyles();
                Hub hub = new Hub();
                Server server = new Server(hub);
                try { server.Start(); }
                catch (SocketException e) { MessageBox.Show("Le port " + Server.Port + " est déjà pris (" + e.Message + ").", "Capri Sync"); return 1; }
                hub.Log("Prêt sur le port " + Server.Port + ".");
                Application.Run(new Window(hub));
                server.Stop();
            }
            return 0;
        }
    }
}
