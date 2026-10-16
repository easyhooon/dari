---
name: release-scope
description: "릴리스 전에 마지막 태그 이후 변경을 영역별로 나눠, 다음 릴리스가 정식(라이브러리+데스크톱), 데스크톱 전용, 검토 필요, 릴리스 불필요 중 무엇인지와 어느 버전(dari / dariDesktop)을 올릴지 판정한다. /release를 실행하기 전, 또는 '버전 뭐 올려야 해?', '데스크톱만 내도 돼?', '이거 릴리스 필요해?'처럼 릴리스 범위를 물을 때 쓴다. 실제 버전 변경·Maven 배포·GitHub 릴리스 생성은 하지 않는다(그건 /release). 사내 앱의 Dari 의존성 업데이트 여부나 PR 리뷰에는 쓰지 않는다."
argument-hint: "[base_tag] [ref]"
user-invocable: true
allowed-tools:
  - Bash
  - Read
---

# 릴리스 범위 판정

`gradle/libs.versions.toml`의 `dari`(라이브러리, Maven Central)와 `dariDesktop`(데스크톱 앱, GitHub 릴리스)은 같은 번호 줄을 쓴다. 이 스킬은 마지막 릴리스 이후 바뀐 파일을 보고 둘 중 무엇을 올려야 하는지 정한다. 판정만 하고 파일은 바꾸지 않는다.

## 인자

- `base_tag` (선택): 비교 기준 태그. 생략하면 가장 최근 `x.y.z` 태그.
- `ref` (선택): 릴리스할 커밋. 생략하면 `origin/main` (릴리스는 main에서 태그를 만들기 때문).

## 절차

### 1. 스크립트 실행

```bash
.claude/skills/release-scope/scripts/release-scope.sh [base_tag] [ref]
```

- 확인: 출력 첫 줄의 `Range:`가 의도한 구간인지 본다. 기준 태그가 예상과 다르면(예: 핫픽스 태그가 따로 있음) 태그를 인자로 직접 넘겨 다시 실행한다.
- 이 단계가 끝나야 2단계로 간다. 판정을 스크립트 없이 `git log`만 보고 정하지 않는다. 이유: 커밋 메시지는 `fix:`여도 라이브러리 파일을 건드릴 수 있고, 판정 기준은 파일 경로다.

### 2. 판정 읽기

스크립트의 `VERDICT`는 영역별 변경으로 정해진다(위에서부터 먼저 걸리는 것).

| VERDICT | 조건 | 올릴 버전 | Maven |
|---|---|---|---|
| `full` | `dari`, `dari-core`, `dari-noop`, `dari-runtime`, `dari-inspector-protocol`의 main 코드 변경 | `dari`와 `dariDesktop` 둘 다 | 배포 |
| `desktop` | `dari-desktop-app`만 변경 | `dariDesktop`만 | 건너뜀 |
| `review` | 공유 빌드 파일(`build.gradle.kts`, `gradle/`, 버전 외 카탈로그 줄 등)만 변경 | 3단계에서 판단 | 판단 |
| `none` | 문서, 샘플, 테스트, CI, 패키징 도구만 변경 | 없음 | 없음 |

- `test`(테스트만), `desktop-tooling`(패키징 스크립트·데스크톱 워크플로)은 그 자체로는 릴리스 사유가 아니다. 다음 릴리스를 만들 때 반영된다.
- 카탈로그에서 `dari`/`dariDesktop` 줄만 바뀐 것(직전 릴리스의 버전 올림)은 변경으로 세지 않는다.
- 출력에 `dariDesktop=<not split yet, follows dari>`가 보이고 판정이 `desktop`이면: 그 ref에는 데스크톱 버전 분리(#158)가 아직 없다. `/release desktop`은 `dariDesktop` 키가 main에 있어야 동작하므로, 분리를 먼저 머지하라고 안내하거나 정식 릴리스로 진행할지 사용자에게 묻는다. 이유: 키 없이 데스크톱 전용으로 내면 설치 파일 버전과 태그가 어긋나 릴리스 워크플로가 실패한다.

### 3. 사람 판단이 필요한 경우만 처리

- `review`이거나 `Check: shared build files changed too`가 나오면: 해당 파일의 diff를 열어, 바뀐 의존성·설정이 라이브러리 모듈에 적용되는지 본다. 라이브러리에 적용되면 `full`, 데스크톱에만 적용되면 `desktop`, 둘 다 아니면 `none`.
- `WARNING: InspectorProtocol.VERSION changed`가 나오면: `full`이고, 앱과 데스크톱이 모두 업데이트해야 서로 연결된다. 릴리스 노트 **Behavior changes**에 반드시 적는다. 이유: 호환성은 이 값으로만 판단하므로, 한쪽만 올린 사용자는 연결이 끊긴다.
- `Note: feat commits present`가 나오면: 사용자 관점에서 새 기능이면 minor, 고침뿐이면 patch를 제안한다. 버전 번호는 사용자가 정한다.

### 4. 결과 보고

다음을 짧게 보고하고 끝낸다. 버전 파일을 고치거나 `/release`를 대신 실행하지 않는다(요청받은 건 판정뿐이고, 릴리스는 되돌리기 어렵다).

- 판정과 근거가 된 영역(파일 몇 개, 대표 경로)
- 올릴 키: `dari`·`dariDesktop` 둘 다 / `dariDesktop`만 / 없음
- 다음 버전 후보: `gh release list --limit 1`로 실제 공개된 최신 릴리스를 확인하고 그보다 크게 잡는다. 비교 기준 태그(`base_tag`)가 아니라 공개된 최신 릴리스가 기준이다. 이유: 설치된 데스크톱 앱 업데이터는 공개된 최신 릴리스보다 높은 버전만 알린다. 과거 구간을 점검한 경우처럼 ref가 이미 릴리스된 태그면 "다음 버전" 대신 그 구간이 어떤 릴리스였어야 했는지만 답한다.
- 이어서 실행할 명령: `full` → `/release <version>`, `desktop` → `/release desktop <version>`, `none` → 릴리스 없음

## 건너뛰고 싶어지는 이유와 왜 안 되는지

- "이번엔 데스크톱만 고쳤으니 뻔하다" → 2.2.0..2.2.1 구간이 실제로 데스크톱 전용이었는데 Maven까지 배포했다. 뻔해 보여도 스크립트가 1분이면 확인한다.
- "라이브러리 테스트만 바꿨으니 full이다" → `src/test`만 바뀌면 사용자가 받는 산출물은 같다. 불필요한 Maven 배포는 앱 쪽에 의미 없는 업데이트 알림을 만든다.
- "프로토콜 파일은 데스크톱에서도 쓰니 desktop이다" → `dari-inspector-protocol`은 Maven으로 앱에도 배포된다. 앱이 받으려면 `full`이어야 한다.

## 검증 기록

| 구간 | 판정 | 실제 |
|---|---|---|
| 2.1.4..2.2.0 | full | 라이브러리 마스킹 변경(#133) — 맞음 |
| 2.2.0..2.2.1 | desktop (+ shared build 확인) | 라이브러리 무변경, Sentry 의존성은 데스크톱 전용 — 데스크톱 전용이 맞았음 |
| 2.2.1..origin/main (2026-10-16) | none | 스테이플링 스크립트(#155)·문서 아이콘만 — 맞음 |

모의 실행(스킬만 읽은 에이전트, 2026-10-16): "PR 157로 2.2.2 내려는데 버전 뭐 올려?" → `desktop`, `/release desktop 2.2.2`. "2.2.0 시점이면 데스크톱만 내도 돼?" → `full`, minor 제안. 두 실행 모두 1~4단계를 따랐고, 분리 전 ref 처리와 다음 버전 기준이 불명확하다는 지적을 반영했다.

## 관리

- Owner: easyhooon
- 다음 검토: 2027-01-16, 또는 모듈 구성(새 라이브러리 모듈, 데스크톱 경로)이 바뀔 때. 바뀌면 `scripts/release-scope.sh`의 `classify()`를 고치고 위 세 구간을 다시 돌려 판정이 그대로인지 확인한다.
- 변경 이력
  - 2026-10-16: 처음 작성. `dari`/`dariDesktop` 분리(#158)에 맞춰 판정 기준 정리.
