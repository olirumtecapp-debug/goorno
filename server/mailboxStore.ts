import fs from "fs";
import path from "path";

const IS_VERCEL = Boolean(process.env.VERCEL || process.env.AWS_LAMBDA_FUNCTION_NAME);
const DATA_DIR = IS_VERCEL ? "/tmp" : path.resolve(process.cwd(), ".data");
const MAILBOX_FILE = path.join(DATA_DIR, "goorno-mailbox.json");

export interface MailboxMessage {
  id: string;
  name: string;
  contact: string; // WhatsApp or Phone
  email?: string;
  preferredReply?: "whatsapp" | "email";
  category: "duvida" | "sugestao" | "problema" | "elogio" | "outro";
  categoryLabel: string;
  subject: string;
  message: string;
  createdAt: string;
  status: "nova" | "lida" | "respondida";
  source: "web" | "app";
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

export async function listAllMessages(): Promise<MailboxMessage[]> {
  try {
    ensureDataDir();
    if (fs.existsSync(MAILBOX_FILE)) {
      const raw = fs.readFileSync(MAILBOX_FILE, "utf-8");
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        return parsed.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
      }
    }
  } catch (err) {
    console.warn("[MailboxStore] Erro ao ler mensagens:", err);
  }
  return inMemoryMailbox.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export async function saveMessage(msg: Omit<MailboxMessage, "id" | "createdAt" | "status">): Promise<MailboxMessage> {
  const all = await listAllMessages();
  const newMessage: MailboxMessage = {
    ...msg,
    id: `msg_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
    createdAt: new Date().toISOString(),
    status: "nova",
  };

  all.unshift(newMessage);
  inMemoryMailbox = all;

  try {
    ensureDataDir();
    fs.writeFileSync(MAILBOX_FILE, JSON.stringify(all, null, 2), "utf-8");
  } catch (err) {
    console.warn("[MailboxStore] Erro ao persistir mensagem:", err);
  }

  return newMessage;
}

export async function markMessageStatus(id: string, status: "nova" | "lida" | "respondida"): Promise<boolean> {
  const all = await listAllMessages();
  const target = all.find((m) => m.id === id);
  if (!target) return false;

  target.status = status;
  inMemoryMailbox = all;

  try {
    ensureDataDir();
    fs.writeFileSync(MAILBOX_FILE, JSON.stringify(all, null, 2), "utf-8");
    return true;
  } catch (err) {
    console.warn("[MailboxStore] Erro ao salvar status:", err);
    return false;
  }
}

export async function deleteMessage(id: string): Promise<boolean> {
  let all = await listAllMessages();
  const initialLen = all.length;
  all = all.filter((m) => m.id !== id);

  if (all.length === initialLen) return false;

  inMemoryMailbox = all;

  try {
    ensureDataDir();
    fs.writeFileSync(MAILBOX_FILE, JSON.stringify(all, null, 2), "utf-8");
    return true;
  } catch (err) {
    console.warn("[MailboxStore] Erro ao excluir mensagem:", err);
    return false;
  }
}
