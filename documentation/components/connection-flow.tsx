'use client';

import { useEffect, useState, type CSSProperties } from 'react';
import { ChevronLeft, ChevronRight, Pause, Play, RotateCcw } from 'lucide-react';

type Node = 'D' | 'A' | 'P';
type Kind = 'request' | 'response' | 'adb';
type Frame = [from: Node, to: Node, label: string, kind: Kind];
type Step = { title: string; body: string; status: [string, string, string]; frames: Frame[] };

const X: Record<Node, number> = { D: 125, A: 340, P: 555 };
const COLOR: Record<Kind, string> = { request: '#7F77DD', response: '#1D9E75', adb: '#888780' };
const FLY_SECONDS = 1;
const GAP_SECONDS = 0.2;
const HOLD_MS = 1800;

const STEPS: Record<'en' | 'ko', Step[]> = {
  en: [
    {
      title: '1. Find the app',
      body: 'The desktop reads the device’s Unix sockets with `adb shell cat /proc/net/unix`. A socket named `dari_<applicationId>` means that app runs Dari.',
      status: ['', '', 'Listening'],
      frames: [
        ['D', 'P', 'cat /proc/net/unix', 'adb'],
        ['P', 'D', '@dari_com.example', 'adb'],
      ],
    },
    {
      title: '2. Open the tunnel',
      body: '`adb forward tcp:0 localabstract:dari_<applicationId>` asks adb for a free local port. `127.0.0.1:<port>` now leads to the socket in the app.',
      status: ['', 'tcp:51234 → @dari_*', 'Listening'],
      frames: [
        ['D', 'A', 'forward tcp:0', 'adb'],
        ['A', 'D', '51234', 'adb'],
      ],
    },
    {
      title: '3. Connect and check the caller',
      body: 'The desktop connects to `127.0.0.1:51234`. The app accepts only uid 2000 (adb shell) or 0 (root), so other apps on the device can’t connect.',
      status: ['Connected', 'tcp:51234 → @dari_*', 'uid 2000 allowed'],
      frames: [['D', 'P', 'connect()', 'request']],
    },
    {
      title: '4. Handshake',
      body: 'Every frame is a 4-byte length followed by JSON. `ClientHello` carries the protocol version; `ServerHello` accepts and describes the app. No answer within 5 seconds means the app is frozen in the background.',
      status: ['Version 1 matched', 'tcp:51234 → @dari_*', 'Session started'],
      frames: [
        ['D', 'P', 'ClientHello v1', 'request'],
        ['P', 'D', 'ServerHello ok', 'response'],
      ],
    },
    {
      title: '5. Subscribe to live messages',
      body: '`Invoke #1 messages.observe` opens a stream. The app keeps sending `Result #1` with `isFinal = false` whenever a message is added or updated.',
      status: ['Stream #1 open', 'tcp:51234 → @dari_*', 'Observer added'],
      frames: [
        ['D', 'P', 'Invoke #1 observe', 'request'],
        ['P', 'D', 'Result #1 added', 'response'],
        ['P', 'D', 'Result #1 updated', 'response'],
        ['P', 'D', 'Result #1 added', 'response'],
      ],
    },
    {
      title: '6. Request and response',
      body: 'Each request gets a new id, and the `Result` with the same id answers it. Stream #1 keeps flowing while #2 and #3 complete. A request times out after 10 seconds.',
      status: ['Waiting on #2, #3', 'tcp:51234 → @dari_*', 'Dispatch by op'],
      frames: [
        ['D', 'P', 'Invoke #2 list', 'request'],
        ['P', 'D', 'Result #2 (500)', 'response'],
        ['P', 'D', 'Result #1 added', 'response'],
        ['D', 'P', 'Invoke #3 detail', 'request'],
        ['P', 'D', 'Result #3', 'response'],
      ],
    },
    {
      title: '7. Disconnect',
      body: '`Cancel #1` stops the stream. Disconnecting closes the socket and removes the tunnel with `adb forward --remove`. If the cable is pulled, pending requests fail at once.',
      status: ['Closed', 'Tunnel removed', 'Listening'],
      frames: [
        ['D', 'P', 'Cancel #1', 'request'],
        ['D', 'P', 'close()', 'request'],
        ['D', 'A', 'forward --remove', 'adb'],
      ],
    },
  ],
  ko: [
    {
      title: '1. 앱 찾기',
      body: '데스크톱 앱이 `adb shell cat /proc/net/unix`로 기기의 Unix 소켓 목록을 읽습니다. `dari_<applicationId>` 소켓이 있으면 그 앱이 Dari를 쓰고 있다는 뜻입니다.',
      status: ['', '', '대기 중'],
      frames: [
        ['D', 'P', 'cat /proc/net/unix', 'adb'],
        ['P', 'D', '@dari_com.example', 'adb'],
      ],
    },
    {
      title: '2. 터널 열기',
      body: '`adb forward tcp:0 localabstract:dari_<applicationId>`로 adb에 빈 로컬 포트를 받습니다. 이제 `127.0.0.1:<port>`가 앱 안의 소켓으로 이어집니다.',
      status: ['', 'tcp:51234 → @dari_*', '대기 중'],
      frames: [
        ['D', 'A', 'forward tcp:0', 'adb'],
        ['A', 'D', '51234', 'adb'],
      ],
    },
    {
      title: '3. 연결과 호출자 확인',
      body: '데스크톱 앱이 `127.0.0.1:51234`로 연결합니다. 앱은 uid 2000(adb shell)이나 0(root)만 받아들이므로 기기의 다른 앱은 연결할 수 없습니다.',
      status: ['연결됨', 'tcp:51234 → @dari_*', 'uid 2000 허용'],
      frames: [['D', 'P', 'connect()', 'request']],
    },
    {
      title: '4. 핸드셰이크',
      body: '모든 프레임은 4바이트 길이 뒤에 JSON이 붙는 형식입니다. `ClientHello`에 프로토콜 버전을 담아 보내면 `ServerHello`가 수락 여부와 앱 정보를 돌려줍니다. 5초 안에 답이 없으면 앱이 백그라운드에서 멈춘 것으로 봅니다.',
      status: ['버전 1 일치', 'tcp:51234 → @dari_*', '세션 시작'],
      frames: [
        ['D', 'P', 'ClientHello v1', 'request'],
        ['P', 'D', 'ServerHello ok', 'response'],
      ],
    },
    {
      title: '5. 실시간 메시지 구독',
      body: '`Invoke #1 messages.observe`가 스트림을 엽니다. 메시지가 추가되거나 바뀔 때마다 앱이 `isFinal = false`인 `Result #1`을 계속 보냅니다.',
      status: ['스트림 #1 열림', 'tcp:51234 → @dari_*', '구독자 등록'],
      frames: [
        ['D', 'P', 'Invoke #1 observe', 'request'],
        ['P', 'D', 'Result #1 added', 'response'],
        ['P', 'D', 'Result #1 updated', 'response'],
        ['P', 'D', 'Result #1 added', 'response'],
      ],
    },
    {
      title: '6. 요청과 응답',
      body: '요청마다 새 id를 붙이고, 같은 id의 `Result`가 그 요청의 응답입니다. 스트림 #1이 흐르는 동안에도 #2와 #3이 섞이지 않고 끝납니다. 요청은 10초가 지나면 타임아웃됩니다.',
      status: ['#2, #3 대기', 'tcp:51234 → @dari_*', 'op별 처리'],
      frames: [
        ['D', 'P', 'Invoke #2 list', 'request'],
        ['P', 'D', 'Result #2 (500)', 'response'],
        ['P', 'D', 'Result #1 added', 'response'],
        ['D', 'P', 'Invoke #3 detail', 'request'],
        ['P', 'D', 'Result #3', 'response'],
      ],
    },
    {
      title: '7. 연결 종료',
      body: '`Cancel #1`이 스트림을 멈춥니다. 연결을 끊으면 소켓을 닫고 `adb forward --remove`로 터널을 지웁니다. 케이블이 빠지면 대기 중인 요청은 바로 실패합니다.',
      status: ['닫힘', '터널 제거', '대기 중'],
      frames: [
        ['D', 'P', 'Cancel #1', 'request'],
        ['D', 'P', 'close()', 'request'],
        ['D', 'A', 'forward --remove', 'adb'],
      ],
    },
  ],
};

const LABELS = {
  en: { prev: 'Previous', next: 'Next', replay: 'Replay', pause: 'Pause', play: 'Autoplay', request: 'Desktop → app', response: 'App → desktop', adb: 'adb command' },
  ko: { prev: '이전', next: '다음', replay: '다시 재생', pause: '자동 재생 끄기', play: '자동 재생', request: '데스크톱 → 앱', response: '앱 → 데스크톱', adb: 'adb 명령' },
};

// Renders `code` spans in step text without pulling in a markdown parser.
function inlineCode(text: string) {
  return text.split('`').map((part, i) =>
    i % 2 ? (
      <code key={i} className="rounded bg-fd-muted px-1 py-0.5 text-[0.85em]">
        {part}
      </code>
    ) : (
      part
    ),
  );
}

export function ConnectionFlow({ lang = 'en' }: { lang?: 'en' | 'ko' }) {
  const steps = STEPS[lang];
  const t = LABELS[lang];
  const [step, setStep] = useState(0);
  const [run, setRun] = useState(0);
  const [auto, setAuto] = useState(true);
  const current = steps[step];

  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) setAuto(false);
  }, []);

  useEffect(() => {
    if (!auto) return;
    const total = current.frames.length * (FLY_SECONDS + GAP_SECONDS) * 1000 + HOLD_MS;
    const id = setTimeout(() => setStep((s) => (s + 1) % steps.length), total);
    return () => clearTimeout(id);
  }, [auto, step, run, current, steps.length]);

  const go = (i: number) => {
    setStep((i + steps.length) % steps.length);
    setRun((r) => r + 1);
  };

  return (
    <div className="not-prose my-6 rounded-xl border bg-fd-card p-4 text-fd-card-foreground">
      <style>{`
        @keyframes dari-fly {
          0% { opacity: 0; transform: translateX(var(--from)); }
          8%, 85% { opacity: 1; }
          100% { opacity: 0; transform: translateX(var(--to)); }
        }
        @media (prefers-reduced-motion: reduce) { .dari-packet { display: none; } }
      `}</style>
      <svg viewBox="0 0 680 240" className="w-full" role="img" aria-label={current.title}>
        {(['D', 'A', 'P'] as Node[]).map((node, i) => (
          <g key={node}>
            <rect
              x={X[node] - 85}
              y={50}
              width={170}
              height={170}
              rx={12}
              strokeWidth={0.75}
              strokeDasharray={node === 'A' ? '4 4' : undefined}
              className={node === 'A' ? 'fill-transparent stroke-fd-border' : 'fill-fd-muted stroke-fd-border'}
            />
            <text x={X[node]} y={76} textAnchor="middle" dominantBaseline="central" fontSize={14} fontWeight={500} className="fill-fd-foreground">
              {['Desktop app', 'adb tunnel', 'Android app'][i]}
            </text>
            <text x={X[node]} y={96} textAnchor="middle" dominantBaseline="central" fontSize={12} className="fill-fd-muted-foreground">
              {['java.net.Socket', 'USB', 'LocalServerSocket'][i]}
            </text>
            <text x={X[node]} y={124} textAnchor="middle" dominantBaseline="central" fontSize={12} className="fill-fd-muted-foreground">
              {current.status[i]}
            </text>
          </g>
        ))}
        <line x1={210} y1={175} x2={470} y2={175} strokeWidth={0.75} strokeDasharray="2 4" className="stroke-fd-border" />
        <g key={`${step}-${run}`}>
          {current.frames.map(([from, to, label, kind], i) => {
            const width = Math.max(70, label.length * 7.2 + 20);
            const style = {
              '--from': `${X[from]}px`,
              '--to': `${X[to]}px`,
              opacity: 0,
              animation: `dari-fly ${FLY_SECONDS}s ease-in-out ${i * (FLY_SECONDS + GAP_SECONDS)}s both`,
            } as CSSProperties;
            return (
              <g key={i} className="dari-packet" style={style}>
                <rect x={-width / 2} y={161} width={width} height={28} rx={6} fill={COLOR[kind]} fillOpacity={0.18} stroke={COLOR[kind]} strokeWidth={0.75} />
                <text x={0} y={175} textAnchor="middle" dominantBaseline="central" fontSize={12} className="fill-fd-foreground">
                  {label}
                </text>
              </g>
            );
          })}
        </g>
      </svg>

      <div className="mt-2 flex flex-wrap gap-4 text-xs text-fd-muted-foreground">
        {(['request', 'response', 'adb'] as Kind[]).map((kind) => (
          <span key={kind} className="flex items-center gap-1.5">
            <span className="inline-block size-2.5 rounded-sm" style={{ background: COLOR[kind] }} />
            {t[kind]}
          </span>
        ))}
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
        <button type="button" onClick={() => go(step - 1)} className="flex items-center gap-1 rounded-md border px-2.5 py-1 hover:bg-fd-accent">
          <ChevronLeft className="size-4" /> {t.prev}
        </button>
        <button type="button" onClick={() => go(step)} className="flex items-center gap-1 rounded-md border px-2.5 py-1 hover:bg-fd-accent">
          <RotateCcw className="size-4" /> {t.replay}
        </button>
        <button type="button" onClick={() => go(step + 1)} className="flex items-center gap-1 rounded-md border px-2.5 py-1 hover:bg-fd-accent">
          {t.next} <ChevronRight className="size-4" />
        </button>
        <button type="button" onClick={() => setAuto((a) => !a)} className="flex items-center gap-1 rounded-md border px-2.5 py-1 hover:bg-fd-accent">
          {auto ? <Pause className="size-4" /> : <Play className="size-4" />} {auto ? t.pause : t.play}
        </button>
        <div className="ml-auto flex gap-1.5">
          {steps.map((s, i) => (
            <button
              key={s.title}
              type="button"
              aria-label={s.title}
              onClick={() => go(i)}
              className={`size-2 rounded-full ${i === step ? 'bg-fd-primary' : 'bg-fd-border'}`}
            />
          ))}
        </div>
      </div>

      <div className="mt-3 rounded-lg bg-fd-muted p-3">
        <p className="text-sm font-medium">{current.title}</p>
        <p className="mt-1 text-sm leading-relaxed text-fd-muted-foreground">{inlineCode(current.body)}</p>
      </div>
    </div>
  );
}
