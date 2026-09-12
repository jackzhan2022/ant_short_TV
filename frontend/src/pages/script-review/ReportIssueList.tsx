import XMarkdown from '@ant-design/x-markdown';
import { reportFindings } from './reportFindings';

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
