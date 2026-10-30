import { asset } from '@kit.AssetStoreKit';
import { util } from '@kit.ArkTS';
import { BusinessError } from '@kit.BasicServicesKit';
import { fileIo as fs } from '@kit.CoreFileKit';
import { ProfileStorage, SecretStorage } from '../core/profiles';

export class FileProfileStorage implements ProfileStorage {
  private path: string;
  constructor(filesDir: string) { this.path = filesDir + '/model-profiles.json'; }
  async read(): Promise<string> { return fs.accessSync(this.path) ? fs.readTextSync(this.path) : ''; }
  async write(value: string): Promise<void> {
    const temp = this.path + '.tmp';
    const file = fs.openSync(temp, fs.OpenMode.CREATE | fs.OpenMode.WRITE_ONLY | fs.OpenMode.TRUNC);
    try {
      const bytes = new util.TextEncoder().encodeInto(value);
      if (fs.writeSync(file.fd,bytes.buffer) !== bytes.byteLength) throw new Error('模型配置写入不完整，原配置未改动');
      fs.fsyncSync(file.fd);
    } finally { fs.closeSync(file); }
    fs.renameSync(temp,this.path);
  }
}
export class AssetSecretStorage implements SecretStorage {
  private encoder: util.TextEncoder = new util.TextEncoder();
  private query(alias: string): asset.AssetMap {
    const map: asset.AssetMap = new Map();
    map.set(asset.Tag.ALIAS,this.encoder.encodeInto(alias));
    return map;
  }
  async put(alias: string, value: string): Promise<void> {
    const bytes = this.encoder.encodeInto(value);
    if (bytes.length > 1024) throw new Error('安全存储的 API Key 不能超过 1024 字节，可关闭保存密钥');
    const map = this.query(alias);
    map.set(asset.Tag.SECRET,bytes);
    map.set(asset.Tag.ACCESSIBILITY,asset.Accessibility.DEVICE_FIRST_UNLOCKED);
    map.set(asset.Tag.SYNC_TYPE,asset.SyncType.NEVER);
    try { await asset.add(map); }
    catch (_) { throw new Error('系统安全存储写入失败，密钥未保存；可关闭保存密钥后重试'); }
    finally { bytes.fill(0); }
  }
  async get(alias: string): Promise<string> {
    const query = this.query(alias);
    query.set(asset.Tag.RETURN_TYPE,asset.ReturnType.ALL);
    try {
      const result = await asset.query(query);
      const bytes = result[0]?.get(asset.Tag.SECRET) as Uint8Array;
      if (!bytes) throw new Error('MISSING_SECRET');
      try { return util.TextDecoder.create('utf-8').decodeToString(bytes); }
      finally { bytes.fill(0); }
    } catch (_) { throw new Error('无法读取已保存密钥，请重新填写；原配置尚未改动'); }
  }
  async remove(alias: string): Promise<void> {
    try { await asset.remove(this.query(alias)); }
    catch (e) { if ((e as BusinessError).code !== 24000002) throw new Error('安全存储密钥清理失败'); }
  }
}
