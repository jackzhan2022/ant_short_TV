type Finding = { title: string; body: string };

export function reportFindings(markdown: string): Finding[] {
  const findings: Finding[] = [];
  let inFindings = false;
  let fence = '';
  let current: Finding | undefined;
  for (const line of markdown.split(/\r?\n/)) {
    const fenceMatch = line.match(/^\s{0,3}(`{3,}|~{3,})/);
    if (fenceMatch) {
      if (!fence) fence = fenceMatch[1];
      else if (
        fenceMatch[1][0] === fence[0] &&
        fenceMatch[1].length >= fence.length
      )
        fence = '';
      if (current) current.body += `${line}\n`;
      continue;
    }
    if (!fence) {
      const section = line.match(/^##\s+(.+)$/);
      if (section) {
        if (inFindings) break;
        inFindings = /主要问题|问题列表|问题清单|审核问题/.test(section[1]);
        continue;
      }
      const heading = inFindings && line.match(/^###\s+(\d+[.、．]\s*.+)$/);
      if (heading) {
        current = { title: heading[1], body: '' };
        findings.push(current);
        continue;
      }
    }
    if (current) current.body += `${line}\n`;
  }
  return findings;
}
