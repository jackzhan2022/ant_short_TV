import XMarkdown from '@ant-design/x-markdown';

type Finding = { title: string; body: string };

function reportFindings(markdown: string): Finding[] {
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

export default function ReportIssueList({ markdown }: { markdown: string }) {
  const findings = reportFindings(markdown);
  return (
    <div
      data-testid="markdown-report-reader"
      style={{
        flex: 1,
        minHeight: 0,
        overflow: 'auto',
        paddingRight: 8,
        overflowWrap: 'anywhere',
      }}
    >
      {findings.length ? (
        <>
          <p>共 {findings.length} 项问题</p>
          <ol
            aria-label="审核问题列表"
            style={{ listStyle: 'none', padding: 0, margin: 0 }}
          >
            {findings.map((finding) => (
              <li
                key={finding.title}
                style={{
                  border:
                    '1px solid var(--ant-color-border-secondary, #e8ebef)',
                  borderRadius: 8,
                  padding: 16,
                  marginBottom: 12,
                }}
              >
                <h3 style={{ marginTop: 0, fontSize: 15 }}>{finding.title}</h3>
                <XMarkdown>
                  {finding.body.trim().replace(/\n\s*---\s*$/, '')}
                </XMarkdown>
              </li>
            ))}
          </ol>
          <details>
            <summary style={{ cursor: 'pointer' }}>
              查看完整报告（含审核范围与限制）
            </summary>
            <XMarkdown>{markdown}</XMarkdown>
          </details>
        </>
      ) : (
        <XMarkdown>{markdown}</XMarkdown>
      )}
    </div>
  );
}
