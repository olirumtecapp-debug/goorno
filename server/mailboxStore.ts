import fs from "fs";
import path from "path";

const IS_VERCEL = Boolean(process.env.VERCEL || process.env.AWS_LAMBDA_FUNCTION_NAME);
const DATA_DIR = IS_VERCEL ? "/tmp" : path.resolve(process.cwd(), ".data");
const MAILBOX_FILE = path.join(DATA_DIR, "goorno-mailbox.json");

const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "expedicao-brasil";
const FIREBASE_API_KEY = process.env.FIREBASE_API_KEY || "";
const COL_MAILBOX = "goorno_mailbox";

function firestoreUrl(collection: string, docId?: string): string {
  const base = `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/databases/(default)/documents/${collection}`;
  const pathUrl = docId ? `${base}/${encodeURIComponent(docId)}` : base;
  return FIREBASE_API_KEY ? `${pathUrl}?key=${encodeURIComponent(FIREBASE_API_KEY)}` : pathUrl;
}

export interface MailboxMessage {
  id: string;
  name: string;
  contact: string; // WhatsApp or Phone
  email?: string;
  preferredReply?: "whatsapp" | "email" | "app";
  category: "duvida" | "sugestao" | "problema" | "elogio" | "outro";
  categoryLabel: string;
  subject: string;
  message: string;
  createdAt: string;
  status: "nova" | "lida" | "respondida";
  source: "web" | "app";
  adminReply?: string;
  repliedAt?: string;
  adminNotes?: string;
}

// In-memory fallback
let inMemoryMailbox: MailboxMessage[] = [
  {
    id: "msg_welcome",
    name: "Sistema GoOrNo",
    contact: "contato@goorno.app",
    category: "outro",
    categoryLabel: "Mensagem do Sistema",
    subject: "Caixa Postal GoOrNo Inicializada",
    message: "A Caixa Postal oficial do GoOrNo está ativa e pronta para receber mensagens, dúvidas e sugestões dos motoristas.",
    createdAt: new Date().toISOString(),
    status: "lida",
    source: "web"
  }
];

function ensureDataDir() {
  if (!fs.existsSync(DATA_DIR)) {
    try {
      fs.mkdirSync(DATA_DIR, { recursive: true });
    } catch (_err) {
      // Ignore directory creation error on read-only environments
    }
  }
}

function readLocalCache(): MailboxMessage[] {
  try {
    ensureDataDir();
    if (fs.existsSync(MAILBOX_FILE)) {
      const raw = fs.readFileSync(MAILBOX_FILE, "utf-8");
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed;
    }
  } catch (_e) {}
  return inMemoryMailbox;
}

function writeLocalCache(messages: MailboxMessage[]) {
  inMemoryMailbox = messages;
  try {
    ensureDataDir();
    fs.writeFileSync(MAILBOX_FILE, JSON.stringify(messages, null, 2), "utf-8");
  } catch (_e) {}
}

export async function listAllMessages(): Promise<MailboxMessage[]> {
  const mapById = new Map<string, MailboxMessage>();

  // 1. Fetch from Firestore
  try {
    const res = await fetch(`${firestoreUrl(COL_MAILBOX)}?pageSize=300`);
    if (res.ok) {
      const json: any = await res.json();
      const docs = json.documents || [];
      for (const d of docs) {
        if (!d.name) continue;
        const raw = d.fields?.payload?.stringValue;
        if (!raw) continue;
        try {
          const msg: MailboxMessage = JSON.parse(raw);
          if (msg && msg.id) {
            mapById.set(msg.id, msg);
          }
        } catch (_parseErr) {}
      }
    }
  } catch (err) {
    console.warn("[MailboxStore] Erro ao listar do Firestore, usando cache local:", err);
  }

  // 2. Mescla com cache local para resiliência
  const localList = readLocalCache();
  for (const item of localList) {
    if (!mapById.has(item.id)) {
      mapById.set(item.id, item);
    }
  }

  const result = Array.from(mapById.values()).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  writeLocalCache(result);
  return result;
}

export async function saveMessage(msg: Omit<MailboxMessage, "id" | "createdAt" | "status">): Promise<MailboxMessage> {
  const newMessage: MailboxMessage = {
    ...msg,
    id: `msg_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
    createdAt: new Date().toISOString(),
    status: "nova",
  };

  // 1. Salva no Firestore
  try {
    const body = {
      fields: {
        payload: { stringValue: JSON.stringify(newMessage) },
        contact: { stringValue: newMessage.contact || "" },
        status: { stringValue: newMessage.status },
        createdAt: { stringValue: newMessage.createdAt },
      },
    };
    await fetch(firestoreUrl(COL_MAILBOX, newMessage.id), {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch (err) {
    console.warn("[MailboxStore] Erro ao persistir no Firestore:", err);
  }

  // 2. Salva no cache local
  const all = readLocalCache();
  all.unshift(newMessage);
  writeLocalCache(all);

  return newMessage;
}

export async function markMessageStatus(
  id: string,
  status: "nova" | "lida" | "respondida",
  adminReply?: string
): Promise<boolean> {
  const all = await listAllMessages();
  const target = all.find((m) => m.id === id);
  if (!target) return false;

  target.status = status;
  if (adminReply !== undefined) {
    target.adminReply = adminReply;
    target.repliedAt = new Date().toISOString();
  }

  // Update in Firestore
  try {
    const body = {
      fields: {
        payload: { stringValue: JSON.stringify(target) },
        contact: { stringValue: target.contact || "" },
        status: { stringValue: target.status },
        updatedAt: { stringValue: new Date().toISOString() },
      },
    };
    await fetch(firestoreUrl(COL_MAILBOX, target.id), {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch (err) {
    console.warn("[MailboxStore] Erro ao atualizar status no Firestore:", err);
  }

  writeLocalCache(all);
  return true;
}

export async function deleteMessage(id: string): Promise<boolean> {
  let all = await listAllMessages();
  const initialLen = all.length;
  all = all.filter((m) => m.id !== id);

  if (all.length === initialLen) return false;

  // Delete from Firestore
  try {
    await fetch(firestoreUrl(COL_MAILBOX, id), {
      method: "DELETE",
    });
  } catch (err) {
    console.warn("[MailboxStore] Erro ao deletar do Firestore:", err);
  }

  writeLocalCache(all);
  return true;
}

/**
 * Retorna as mensagens de um motorista específico (por telefone/whatsapp ou email)
 */
export async function listDriverMessages(phoneOrEmail: string): Promise<MailboxMessage[]> {
  const cleanTarget = phoneOrEmail.replace(/\D/g, "");
  const all = await listAllMessages();

  return all.filter((m) => {
    const cleanMsgContact = (m.contact || "").replace(/\D/g, "");
    if (cleanTarget.length >= 8 && cleanMsgContact.includes(cleanTarget.slice(-8))) {
      return true;
    }
    if (phoneOrEmail.includes("@") && m.email && m.email.toLowerCase() === phoneOrEmail.toLowerCase().trim()) {
      return true;
    }
    return false;
  });
}
