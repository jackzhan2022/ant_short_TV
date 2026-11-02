import fs from 'node:fs';
import path from 'node:path';

export function validateOutputName(value: unknown): string {
  if (value === undefined || value === '') return '';
  if (typeof value !== 'string') throw new Error('图片名称无效。');
  const name = value.trim().replace(/\.(png|jpe?g|webp)$/i, '');
  if (
    !name ||
    name.length > 80 ||
    /[<>:"/\\|?*]/.test(name) ||
    [...name].some((char) => char.charCodeAt(0) < 32) ||
    /[. ]$/.test(name) ||
    /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(name)
  ) {
    throw new Error(
      '图片名称请使用 1～80 个有效字符，不能含路径、特殊符号或 Windows 保留名称。',
    );
  }
  return name;
}

export function automaticName(date: string): string {
  const value = new Date(date);
  const pad = (number: number) => String(number).padStart(2, '0');
  return `ANTV生图_${value.getFullYear()}${pad(value.getMonth() + 1)}${pad(value.getDate())}_${pad(value.getHours())}${pad(value.getMinutes())}${pad(value.getSeconds())}`;
}

/** Exclusive creation avoids overwriting even when another process creates the same filename. */
export function copyWithoutOverwrite(
  source: string,
  directory: string,
  name: string,
  extension: string,
): string {
  if (!['png', 'jpeg', 'webp'].includes(extension))
    throw new Error('输出格式无效。');
  const safeName = validateOutputName(name);
  if (!safeName) throw new Error('输出名称不能为空。');
  fs.mkdirSync(directory, { recursive: true });
  for (let index = 1; index <= 10000; index++) {
    const destination = path.join(
      directory,
      `${safeName}${index === 1 ? '' : `_${index}`}.${extension}`,
    );
    try {
      fs.copyFileSync(source, destination, fs.constants.COPYFILE_EXCL);
      return destination;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'EEXIST') throw error;
    }
  }
  throw new Error('同名文件过多，请更换图片名称。');
}
