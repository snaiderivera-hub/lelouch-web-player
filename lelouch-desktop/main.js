const { app, BrowserWindow } = require('electron');
const express = require('express');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const { Readable } = require('stream');

let mainWindow;

const appRoot = app.isPackaged 
    ? path.join(process.resourcesPath, 'iptv-app') 
    : path.join(__dirname, '..', 'iptv-app');

function createServers() {
    // 1. Web Server (Puerto 8686)
    const webApp = express();
    webApp.use(express.static(appRoot));
    
    // 2. Proxy Server (Puerto 7878)
    const proxyApp = express();
    proxyApp.use(cors());
    
    proxyApp.all('/proxy', async (req, res) => {
        let targetUrl = req.query.target;
        if (!targetUrl) return res.status(400).send('Target URL required');
        
        if (targetUrl.includes('redworld.pro:8888')) {
            targetUrl = targetUrl.replace('redworld.pro:8888', 'redworld.pro:8880');
        } else if (targetUrl.includes('redworld.pro/') && !targetUrl.includes('redworld.pro:')) {
            targetUrl = targetUrl.replace('redworld.pro/', 'redworld.pro:8880/');
        }
        
        const controller = new AbortController();
        req.on('close', () => controller.abort());

        try {
            const fetchOpts = {
                method: req.method,
                headers: { ...req.headers },
                redirect: 'follow',
                signal: controller.signal
            };
            
            delete fetchOpts.headers['host'];
            delete fetchOpts.headers['referer'];
            delete fetchOpts.headers['origin'];
            Object.keys(fetchOpts.headers).forEach(k => {
                if (k.toLowerCase().startsWith('sec-')) delete fetchOpts.headers[k];
            });
            
            const response = await fetch(targetUrl, fetchOpts);
            
            // Replicar headers seguros
            const ce = response.headers.get('content-encoding') || '';
            const isCompressed = ce.toLowerCase().includes('gzip') || ce.toLowerCase().includes('br') || ce.toLowerCase().includes('deflate');

            response.headers.forEach((val, key) => {
                const lowerKey = key.toLowerCase();
                if (lowerKey === 'transfer-encoding' || lowerKey === 'connection') return;
                
                if (isCompressed && (lowerKey === 'content-encoding' || lowerKey === 'content-length')) {
                    return;
                }
                
                res.setHeader(key, val);
            });
            
            res.status(response.status);
            
            // Stream the response directly to avoid buffering issues and decoding mismatch
            if (response.body) {
                const readable = Readable.fromWeb(response.body);
                readable.on('error', (err) => {
                    if (err.name !== 'AbortError') {
                        console.error('Stream error:', err.message);
                    }
                });
                readable.pipe(res);
            } else {
                res.end();
            }
            
        } catch (err) {
            if (err.name === 'AbortError') return; // Cancelado por el usuario
            console.error("Proxy error:", err);
            res.status(500).send('Proxy Error: ' + err.message);
        }
    });

    try {
        // Escribir config para el frontend
        const configPath = path.join(appRoot, 'src', 'proxy-config.js');
        if (fs.existsSync(path.dirname(configPath))) {
            fs.writeFileSync(configPath, 'window.IPTV_PROXY_PORT = 7878;', 'utf8');
        }
    } catch (e) {
        console.error("No se pudo escribir proxy-config.js", e);
    }

    webApp.listen(8686, '127.0.0.1');
    proxyApp.listen(7878, '127.0.0.1');
}

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 720,
        show: false,
        autoHideMenuBar: true,
        backgroundColor: '#03040b',
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true
        },
        icon: path.join(__dirname, 'build', 'icon.ico')
    });
    
    // Crear Splash Screen
    const splash = new BrowserWindow({
        width: 800,
        height: 450,
        transparent: false,
        frame: false,
        alwaysOnTop: true,
        show: true
    });
    
    splash.loadFile('splash.html');

    // Iniciar servidores
    createServers();

    // Cargar la app web principal en la ventana oculta
    mainWindow.loadURL('http://127.0.0.1:8686');

    mainWindow.once('ready-to-show', () => {
        // Simular tiempo de carga cinematográfico
        setTimeout(() => {
            if (!splash.isDestroyed()) {
                splash.close();
            }
            mainWindow.maximize();
            mainWindow.show();
        }, 3000);
    });

    // Fallback de seguridad: si la página falla al renderizar, forzar mostrar
    setTimeout(() => {
        if (!mainWindow.isVisible()) {
            if (!splash.isDestroyed()) splash.close();
            mainWindow.maximize();
            mainWindow.show();
        }
    }, 8000);
}

const gotTheLock = app.requestSingleInstanceLock();
if (!gotTheLock) {
    app.quit();
} else {
    app.whenReady().then(() => {
        createWindow();

        app.on('activate', function () {
            if (BrowserWindow.getAllWindows().length === 0) createWindow();
        });
    });
}

app.on('window-all-closed', function () {
    if (process.platform !== 'darwin') app.quit();
});
