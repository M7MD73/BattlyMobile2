import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";

const SOURCE_URL = "https://api.battlylauncher.com/battlylauncher/launcher/news-launcher/mobile.json";
const SOURCE_FILE = process.env.NEWS_SOURCE_FILE;
const OUTPUT_DIR = path.resolve("app_pojavlauncher/src/main/assets/news");
const CACHE_FILE = path.resolve("app_pojavlauncher/build/news-translation-cache.json");
const USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36 Edg/153.0.0.0";

const TARGETS = new Map([
    ["en", "en"],
    ["pt", "pt"],
    ["ru", "ru"],
    ["zh", "zh-Hans"],
    ["hi", "hi"],
    ["id", "id"],
    ["de", "de"],
    ["fr", "fr"],
    ["ja", "ja"]
]);

const WHATS_NEW_STRINGS_ES = {
    battly_whats_new_title: "Battly Mobile 2.2.5",
    battly_whats_new_desc: "Mejor compatibilidad con Minecraft 26.3, audio nativo para mods, novedades traducidas y correcciones de pantalla.",
    battly_whats_new_controllers_title: "Novedades siempre actualizadas",
    battly_whats_new_controllers_desc: "Battly consulta la API en el idioma del dispositivo y actualiza su caché cuando cambia el contenido del servidor.",
    battly_whats_new_instances_title: "Minecraft 26.3 corregido",
    battly_whats_new_instances_desc: "Corregidos el inicio, las texturas y el renderizado de Minecraft 26.3 en Vanilla, Fabric y versiones personalizadas.",
    battly_whats_new_compatibility_title: "Plugins nativos y Distant Horizons",
    battly_whats_new_compatibility_desc: "Battly reconoce plugins de librerías nativas y permite iniciar mods compatibles como Distant Horizons cuando el plugin necesario está instalado.",
    battly_whats_new_worlds_title: "Micrófono disponible para los mods",
    battly_whats_new_worlds_desc: "Los mods pueden detectar el micrófono de Android, elegir entre las entradas disponibles y capturar voz sin enviar el audio del juego.",
    battly_whats_new_recovery_title: "Inicio y cierre más precisos",
    battly_whats_new_recovery_desc: "Mejorados Vulkan y la orientación, el tamaño de la ventana de Minecraft, los argumentos JVM y la detección de cierres normales.",
    battly_whats_new_action_start: "Descubrir la 2.2.5",
    battly_whats_new_transition_title: "Battly 2.2.5 está listo",
    battly_whats_new_transition_desc: "Tus instancias y ajustes existentes se mantienen sin cambios."
};

const WHATS_NEW_STRINGS_EN = {
    battly_whats_new_title: "Battly Mobile 2.2.5",
    battly_whats_new_desc: "Better Minecraft 26.3 compatibility, native audio for mods, translated news and display fixes.",
    battly_whats_new_controllers_title: "News that stays up to date",
    battly_whats_new_controllers_desc: "Battly requests the API in the device language and updates its cache whenever the server content changes.",
    battly_whats_new_instances_title: "Minecraft 26.3 fixed",
    battly_whats_new_instances_desc: "Fixed startup, textures and rendering for Minecraft 26.3 across Vanilla, Fabric and custom versions.",
    battly_whats_new_compatibility_title: "Native plugins and Distant Horizons",
    battly_whats_new_compatibility_desc: "Battly detects native library plugins and starts compatible mods such as Distant Horizons when the required plugin is installed.",
    battly_whats_new_worlds_title: "Microphone access for mods",
    battly_whats_new_worlds_desc: "Mods can detect the Android microphone, select an available input and capture voice without transmitting game audio.",
    battly_whats_new_recovery_title: "More accurate startup and shutdown",
    battly_whats_new_recovery_desc: "Improved Vulkan and orientation handling, Minecraft window sizing, JVM arguments and detection of normal exits.",
    battly_whats_new_action_start: "Explore 2.2.5",
    battly_whats_new_transition_title: "Battly 2.2.5 is ready",
    battly_whats_new_transition_desc: "Your existing instances and settings remain unchanged."
};

const RESOURCE_DIRS = new Map([
    ["en", ["values"]], ["es", ["values-es"]],
    ["pt", ["values-pt", "values-pt-rBR"]], ["ru", ["values-ru"]],
    ["zh", ["values-zh-rCN", "values-zh-rTW"]], ["hi", ["values-hi"]],
    ["id", ["values-in"]], ["de", ["values-de"]],
    ["fr", ["values-fr"]], ["ja", ["values-ja"]]
]);

const PROTECTED_TERMS = [
    "Google Play Services", "Battly Launcher", "Battly Mobile", "Battly Worlds",
    "BattlyWorlds", "Battly Workspace", "Battly Social", "Battly Champions",
    "Battly Client", "MoonLightMC", "ForgeOptiFine", "LegacyFabric", "MobileGlues",
    "NeoForge", "OptiFine", "Minecraft", "VPNService", "Terracotta", "Battly+",
    "LWJGL", "Sodium", "Fabric", "Forge", "Quilt", "Android", "Java", "JVM",
    "LTW", "@here", "@everyone"
];

let bingConfig;

async function fetchJson(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: { "user-agent": USER_AGENT, ...(options.headers || {}) }
    });
    if (!response.ok) throw new Error(`${url} returned HTTP ${response.status}`);
    return response.json();
}

async function loadBingConfig() {
    if (bingConfig) return bingConfig;
    const response = await fetch("https://www.bing.com/translator", {
        headers: { "user-agent": USER_AGENT }
    });
    if (!response.ok) throw new Error(`Bing Translator returned HTTP ${response.status}`);
    const body = await response.text();
    const ig = body.match(/IG:"([^"]+)"/)?.[1];
    const iid = body.match(/data-iid="([^"]+)"/)?.[1];
    const params = body.match(/params_AbusePreventionHelper\s?=\s?([^\]]+\])/)?.[1];
    if (!ig || !iid || !params) throw new Error("Unable to read Bing Translator session data");
    const [key, token] = JSON.parse(params);
    bingConfig = { ig, iid, key, token };
    return bingConfig;
}

function protectText(text) {
    const values = [];
    const protect = value => {
        const index = values.push(value) - 1;
        return `ZXQ${String(index).padStart(4, "0")}QXZ`;
    };
    let protectedText = text.replace(/https?:\/\/[^\s)]+/g, protect);
    for (const term of PROTECTED_TERMS) {
        protectedText = protectedText.replaceAll(term, () => protect(term));
    }
    return {
        text: protectedText,
        restore(translated) {
            return translated.replace(/ZXQ\s*(\d{4})\s*QXZ/gi, (_, index) => values[Number(index)] ?? _);
        }
    };
}

function chunkText(text, limit = 2750) {
    if (text.length <= limit) return [text];
    const lines = text.split("\n");
    const chunks = [];
    let current = "";
    for (const line of lines) {
        const candidate = current ? `${current}\n${line}` : line;
        if (candidate.length <= limit) {
            current = candidate;
            continue;
        }
        if (current) chunks.push(current);
        if (line.length <= limit) {
            current = line;
            continue;
        }
        for (let start = 0; start < line.length; start += limit) {
            chunks.push(line.slice(start, start + limit));
        }
        current = "";
    }
    if (current || chunks.length === 0) chunks.push(current);
    return chunks;
}

async function translateChunk(text, target) {
    if (!text.trim()) return text;
    const config = await loadBingConfig();
    const url = new URL("https://www.bing.com/ttranslatev3");
    url.search = new URLSearchParams({
        isVertical: "1",
        IG: config.ig,
        IID: config.iid,
        ref: "TThis",
        edgepdftranslator: "1"
    });
    const body = new URLSearchParams({
        fromLang: "es",
        to: target,
        text,
        token: config.token,
        key: String(config.key),
        tryFetchingGenderDebiasedTranslations: "true"
    });
    const response = await fetch(url, {
        method: "POST",
        headers: {
            "content-type": "application/x-www-form-urlencoded",
            "user-agent": USER_AGENT,
            referer: "https://www.bing.com/translator"
        },
        body
    });
    if (!response.ok) throw new Error(`Bing translation returned HTTP ${response.status}`);
    const result = await response.json();
    const translated = result?.[0]?.translations?.[0]?.text;
    if (typeof translated !== "string") throw new Error("Bing returned an invalid translation");
    return translated;
}

async function translateText(text, target) {
    const protectedValue = protectText(text);
    const chunks = chunkText(protectedValue.text);
    const translated = [];
    for (const chunk of chunks) translated.push(await translateChunk(chunk, target));
    return protectedValue.restore(translated.join("\n"));
}

async function loadCache() {
    try {
        return JSON.parse(await readFile(CACHE_FILE, "utf8"));
    } catch {
        return {};
    }
}

async function saveCache(cache) {
    await mkdir(path.dirname(CACHE_FILE), { recursive: true });
    await writeFile(CACHE_FILE, `${JSON.stringify(cache, null, 2)}\n`, "utf8");
}

function validateCatalog(source, translated, locale) {
    if (translated.length !== source.length) {
        throw new Error(`${locale}: expected ${source.length} news entries, found ${translated.length}`);
    }
    source.forEach((item, index) => {
        const candidate = translated[index];
        if (candidate.id !== item.id || candidate.author !== item.author
                || candidate.publish_date !== item.publish_date
                || !candidate.title?.trim() || !candidate.content?.trim()) {
            throw new Error(`${locale}: invalid translated entry ${item.id}`);
        }
    });
}

function polishTranslation(text, locale) {
    const version = "(Battly Mobile \\d+(?:\\.\\d+)+)";
    const replacements = {
        en: [[new RegExp(`${version} now available`, "g"), "$1 is now available"]],
        de: [[new RegExp(`Bereits verfügbar ${version}`, "g"), "$1 ist jetzt verfügbar"]],
        fr: [
            [new RegExp(`Déjà disponible ${version}`, "g"), "$1 est maintenant disponible"],
            [/qui vous em\*\*\*, Google/g, "va te faire voir, Google"]
        ],
        hi: [[new RegExp(`अब उपलब्ध ${version}`, "g"), "$1 अब उपलब्ध है"]],
        id: [[new RegExp(`${version} sudah tersedia`, "g"), "$1 kini tersedia"]],
        ja: [[new RegExp(`${version} が利用可能になりました`, "g"), "$1 を公開しました"]],
        zh: [[new RegExp(`${version} 已可用`, "g"), "$1 现已发布"]]
    };
    return (replacements[locale] || []).reduce(
            (value, [pattern, replacement]) => value.replace(pattern, replacement), text);
}

function encodeAndroidString(value) {
    return value.replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("'", "\\'");
}

async function syncWhatsNewResources(cache) {
    const translations = new Map([
        ["en", WHATS_NEW_STRINGS_EN],
        ["es", WHATS_NEW_STRINGS_ES]
    ]);
    for (const [locale, target] of TARGETS) {
        if (locale === "en") continue;
        const translated = {};
        for (const [key, value] of Object.entries(WHATS_NEW_STRINGS_ES)) {
            const cacheKey = `welcome:${locale}:${key}:${value}`;
            if (!cache[cacheKey]) cache[cacheKey] = await translateText(value, target);
            translated[key] = cache[cacheKey];
        }
        translations.set(locale, translated);
        await saveCache(cache);
    }

    const resourceRoot = path.resolve("app_pojavlauncher/src/main/res");
    const supportedDirs = new Set([...RESOURCE_DIRS.values()].flat());
    const keys = Object.keys(WHATS_NEW_STRINGS_ES);
    for (const [locale, dirs] of RESOURCE_DIRS) {
        for (const dir of dirs) {
            const file = path.join(resourceRoot, dir, "strings.xml");
            let xml = await readFile(file, "utf8");
            for (const key of keys) {
                const pattern = new RegExp(`<string name="${key}">[\\s\\S]*?<\\/string>`);
                if (!pattern.test(xml)) throw new Error(`${dir}: missing ${key}`);
                xml = xml.replace(pattern,
                        `<string name="${key}">${encodeAndroidString(translations.get(locale)[key])}</string>`);
            }
            await writeFile(file, xml, "utf8");
        }
    }

    const { readdir } = await import("node:fs/promises");
    for (const entry of await readdir(resourceRoot, { withFileTypes: true })) {
        if (!entry.isDirectory() || !entry.name.startsWith("values-") || supportedDirs.has(entry.name)) continue;
        const file = path.join(resourceRoot, entry.name, "strings.xml");
        let xml;
        try {
            xml = await readFile(file, "utf8");
        } catch {
            continue;
        }
        const original = xml;
        for (const key of keys) {
            xml = xml.replace(new RegExp(`\\r?\\n\\s*<string name="${key}">[\\s\\S]*?<\\/string>`), "");
        }
        if (xml !== original) await writeFile(file, xml, "utf8");
    }
    console.log("Updated the 2.2.5 welcome in 10 languages; other locales fall back to English.");
}

async function main() {
    const source = SOURCE_FILE
            ? JSON.parse(await readFile(path.resolve(SOURCE_FILE), "utf8"))
            : await fetchJson(SOURCE_URL);
    if (!Array.isArray(source) || source.length === 0) throw new Error("The source news catalog is empty");
    const catalogs = new Map([["es", source]]);
    const cache = await loadCache();

    for (const [locale, target] of TARGETS) {
        const catalog = [];
        for (let start = 0; start < source.length; start += 3) {
            const translatedBatch = await Promise.all(source.slice(start, start + 3).map(async item => {
                const titleKey = `${locale}:${item.id}:title:${item.title}`;
                const contentKey = `${locale}:${item.id}:content:${item.content}`;
                if (!cache[titleKey]) cache[titleKey] = await translateText(item.title, target);
                if (!cache[contentKey]) cache[contentKey] = await translateText(item.content, target);
                process.stdout.write(`${locale}:${item.id} `);
                return {
                    ...item,
                    title: polishTranslation(cache[titleKey], locale),
                    content: polishTranslation(cache[contentKey], locale)
                };
            }));
            catalog.push(...translatedBatch);
            await saveCache(cache);
        }
        process.stdout.write("\n");
        validateCatalog(source, catalog, locale);
        catalogs.set(locale, catalog);
    }

    await mkdir(OUTPUT_DIR, { recursive: true });
    for (const [locale, catalog] of catalogs) {
        validateCatalog(source, catalog, locale);
        await writeFile(
                path.join(OUTPUT_DIR, `mobile.${locale}.json`),
                `${JSON.stringify(catalog, null, 2)}\n`,
                "utf8");
    }
    await writeFile(
            path.join(OUTPUT_DIR, "mobile.json"),
            `${JSON.stringify(source, null, 2)}\n`,
            "utf8");
    if (process.env.SYNC_WHATS_NEW_RESOURCES === "true") {
        await syncWhatsNewResources(cache);
    }
    console.log(`Wrote ${catalogs.size} complete catalogs with ${source.length} entries each.`);
}

await main();
