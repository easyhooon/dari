---
name: resolve-coderabbit-review
description: "PR에 달린 CodeRabbit 미해결 코멘트를 읽고, 코드에 반영하거나 거절 사유를 대댓글로 남기는 Skill."
argument-hint: "[PR번호] [--brief]"
user-invocable: true
disable-model-invocation: true
allowed-tools:
  - Bash
  - Read
  - Edit
  - Write
  - Glob
  - Grep
  - Task
  - AskUserQuestion
---

# CodeRabbit 리뷰 반영 Skill

현재 브랜치의 PR에 달린 CodeRabbit 미해결 코멘트를 수집하고, 각 코멘트를 분석하여 코드를 수정하거나 거절 사유를 댓글로 남긴다.

## 인자

- `$ARGUMENTS` — PR 번호 (선택). 생략하면 현재 브랜치의 PR을 자동 감지한다.
- `--brief` — 최종 요약에서 코드레벨 수정 상세 설명을 생략한다. 기본값은 코드레벨 상세 설명 포함.

## 실행 절차

### 1단계: PR 정보 확인

PR 번호가 인자로 주어지면 그것을 사용하고, 없으면 현재 브랜치의 PR을 자동 감지한다.

```bash
# 인자가 없을 때 자동 감지
gh pr view --json number,headRefName,baseRefName,url,isDraft
```

PR이 없으면 사용자에게 알리고 중단한다.

**Draft PR인 경우** (`isDraft == true`): "Draft PR에는 리뷰 반영을 수행하지 않습니다." 메시지를 출력하고 중단한다.

### 1.5단계: 학습 패턴 로드

`learned-patterns.md` 파일이 존재하면 읽어서, 이전에 반복된 패턴 정보를 4단계 판단에 참고한다. 이전에 일관되게 반영 또는 거절한 패턴이 있다면 같은 방향으로 판단하는 것을 기본으로 한다.

### 2단계: 미해결 CodeRabbit 코멘트 수집

아래 GraphQL 쿼리로 **미해결(isResolved=false)** 스레드 중 **CodeRabbit가 작성한 것만** 필터링한다.

```bash
gh api graphql -f query='
{
  repository(owner: "{OWNER}", name: "{REPO}") {
    pullRequest(number: {PR_NUMBER}) {
      reviewThreads(first: 100) {
        nodes {
          id
          isResolved
          comments(first: 10) {
            nodes {
              databaseId
              author { login }
              body
              path
              line
              originalLine
              diffHunk
            }
          }
        }
      }
    }
  }
}'
```

필터 조건:
- `isResolved == false`
- 스레드의 첫 번째 코멘트 작성자가 `coderabbitai[bot]` 또는 `coderabbitai`
- CodeRabbit의 PR 요약 코멘트(리뷰 본문이 아닌 일반 요약)는 제외
- **이미 처리된 코멘트는 스킵**: 스레드 내 대댓글에 아래 문구가 포함되어 있으면 이전 실행에서 이미 처리된 것이므로 수집 대상에서 제외한다:
  - `✅ Addressed in` — 이미 반영됨 (현행 영어 템플릿)
  - `✅` + `반영완료` — 이미 반영됨 (구 한글 템플릿, 하위 호환)
  - `Thanks for the suggestion. After review, we've decided not to apply this change.` — 이미 거절됨 (현행 영어 템플릿)
  - `해당 제안은 검토 결과 현재 반영하지 않았습니다` — 이미 거절됨 (구 한글 템플릿, 하위 호환)

수집된 코멘트가 없으면 "미해결 CodeRabbit 코멘트가 없습니다." 출력 후 종료한다.

### 3단계: 중요도별 정렬

CodeRabbit 코멘트 본문에서 중요도를 판단하여 우선순위순으로 정렬한다:

1. **보안/크래시 관련** — 최우선 (NPE, 인젝션, 메모리릭 등 키워드 감지)
2. **버그/로직 오류 관련** — 높음
3. **코드 품질/가독성 관련** — 보통
4. **스타일/컨벤션 관련** — 낮음

코멘트 본문에 명시적 심각도 라벨이 있으면 해당 라벨을 우선 사용한다.

### 4단계: 각 코멘트 순회 처리

코멘트마다 아래 과정을 **순차적으로** 수행한다:

#### 4-1. 코멘트 내용 분석

- **파일 경로** (`path`): 수정 대상 파일
- **라인 번호** (`line` 또는 `originalLine`): 수정 대상 위치
- **제안 diff**: 본문의 `` ```suggestion `` 또는 `` ```diff `` 블록 파싱
- **지적 내용**: 무엇이 문제이고 어떻게 변경하라는 것인지 파악
- **코멘트 요약**: 테이블에 들어갈 요약은 **20~25자**로 작성한다. 어려운 경우 최대 **30자**까지 허용한다.

#### 4-2. 해당 파일 읽기 및 컨텍스트 이해

- `Read` 도구로 해당 파일을 읽는다
- 주변 코드, 프로젝트 구조, 기존 패턴을 파악한다
- 필요하면 관련 파일도 추가로 읽는다

#### 4-3. 반영 여부 판단

**반영하는 경우 (실질적 리스크가 있을 때 우선):**
- 크래시 위험 (NPE, race condition 등)
- 메모리릭 리스크 (리소스 미해제, 콜백 누수 등)
- 보안 취약점 (하드코딩된 키, 인젝션 등)
- 스레드 안전성 문제
- 누락된 에러 처리
- 명백한 코드 품질 개선
- 인라인 FQN 사용 → import 문으로 교체 (프로젝트 CLAUDE.md 규칙)

**거절하는 경우 (실용적 판단 포함):**
- 수정 범위가 과하게 커지는 제안 (변경 영향도 대비 이점이 적음)
- 오버엔지니어링이 우려되는 제안 (과도한 추상화, 불필요한 패턴 도입)
- 현재 필요하지 않은 기능이나 방어 코드를 추가하려는 제안
- 프로젝트의 의도된 설계를 변경하려는 제안
- 아직 구현 예정인 기능에 대한 지적 (TODO 성격) — TODO 코멘트가 남아있는 미구현 클릭 핸들러, 드롭다운, 네비게이션 등 포함
- 이미 다른 방식으로 처리되고 있는 문제
- 기술적으로 약간의 개선이 있더라도 위 사유에 해당한다고 판단되면 거절할 수 있다

#### 4-4a. 반영하는 경우

1. 코드 수정 (`Edit` 도구 사용)
2. 수정 내용 커밋 (코멘트 1개당 1커밋)
   - 커밋 메시지 형식: `fix: {수정 내용 요약}` 또는 `refactor: {수정 내용 요약}`
   - **절대로 커밋 메시지에 Claude 관련 문구를 넣지 않는다**
   - **절대로 `--no-verify`를 사용하지 않는다**
3. 푸시
4. 해당 스레드에 대댓글 작성:

**언어 정책**: 이 저장소는 라이브러리 프로젝트로 전세계 사용자가 보기 때문에, **리뷰 응답 대댓글은 영어로 작성한다**. 반영 완료 메시지 예시:

```bash
gh api repos/{OWNER}/{REPO}/pulls/{PR_NUMBER}/comments \
  -f body="✅ Addressed in https://github.com/{OWNER}/{REPO}/commit/{FULL_COMMIT_HASH}" \
  -F in_reply_to={FIRST_COMMENT_DATABASE_ID}
```

5. 해당 리뷰 스레드를 resolve 처리한다:

```bash
gh api graphql -f query='
mutation {
  resolveReviewThread(input: {threadId: "{THREAD_ID}"}) {
    thread { isResolved }
  }
}'
```

#### 4-4b. 반영하지 않는 경우 (사용자 확인 필수)

**거절은 즉시 처리하지 않는다.** 반드시 사용자에게 먼저 확인을 받아야 한다.

1. 거절하려는 코멘트 목록을 모아서 사용자에게 아래 형식으로 제시한다:

```text
## 거절 예정 코멘트 확인 요청

아래 코멘트들을 거절하려고 합니다. 확인 부탁드립니다.

### 1. {파일경로}:{라인번호} ({중요도})
- **코멘트 내용**: {CodeRabbit가 지적한 내용 요약}
- **거절 사유**: {구체적인 거절 이유}

### 2. {파일경로}:{라인번호} ({중요도})
- **코멘트 내용**: {CodeRabbit가 지적한 내용 요약}
- **거절 사유**: {구체적인 거절 이유}

이대로 거절 처리해도 괜찮을까요?
```

2. 사용자가 승인하면, 각 코멘트에 **영어로** 대댓글로 거절 사유를 작성한다 (라이브러리 저장소이므로 전세계 사용자가 읽을 수 있도록):

```bash
gh api repos/{OWNER}/{REPO}/pulls/{PR_NUMBER}/comments \
  -f body="Thanks for the suggestion. After review, we've decided not to apply this change.

**Reason**: {clear, specific reason in English}" \
  -F in_reply_to={FIRST_COMMENT_DATABASE_ID}
```

3. 사용자가 일부 코멘트에 대해 "그건 반영해줘"라고 하면, 해당 코멘트는 4-4a 절차로 전환하여 반영한다.

거절 사유는 반드시:
- 왜 반영하지 않는지 근거를 포함한다 (기술적 사유, 실용적 판단 모두 가능)
- 정중하고 명확하게 작성한다
- 필요하면 향후 대응 계획도 언급한다

### 5단계: PR 본문 업데이트 (선택적)

모든 코멘트 처리가 완료된 후, 이번에 반영한 수정사항 중 **PR 본문에 반영할 만한 변경**이 있는지 판단한다.

#### 업데이트하는 경우

- 크래시/보안 등 중요 버그 수정이 반영된 경우
- 핵심 로직이나 동작 방식이 변경된 경우
- PR의 주요 변경사항 설명에 영향을 주는 수정인 경우

#### 업데이트하지 않는 경우

- 코드 스타일, 린트, 포맷팅 수정
- 변수명 변경 등 사소한 리팩토링
- PR 본문의 설명과 무관한 부분적 개선

#### 업데이트 방법

기존 PR 본문을 읽고, 변경사항을 기존 내용에 자연스럽게 반영하여 갱신한다. 기존 구조와 톤을 유지하면서 수정된 부분만 업데이트한다.

```bash
# PR 본문 읽기
gh pr view {PR_NUMBER} --json body --jq '.body'

# PR 본문 갱신
gh pr edit {PR_NUMBER} --body "$(cat <<'EOF'
{갱신된 PR 본문}
EOF
)"
```

### 6단계: PR 코멘트로 처리 요약 게시 (누적 방식)

모든 코멘트 처리가 완료되면, **PR에 일반 코멘트로 처리 요약을 게시**한다.
여러 번 실행해도 요약 코멘트가 중복되지 않도록, 기존 코멘트가 있으면 병합하여 갱신한다.

**언어 정책**: PR 요약 코멘트도 **영어로 작성**한다 (라이브러리 저장소 — 리뷰 응답 대댓글과 동일한 정책).

#### 6-1. 기존 요약 코멘트 검색

PR의 일반 코멘트(issue comment) 중 `🤖 CodeRabbit Review Resolution` 헤더가 포함된 코멘트를 검색한다.

```bash
gh api repos/{OWNER}/{REPO}/issues/{PR_NUMBER}/comments --jq '.[] | select(.body | contains("🤖 CodeRabbit Review Resolution")) | {id, body}'
```

#### 6-2a. 기존 코멘트가 있는 경우 (병합)

1. 기존 코멘트의 반영/거절 테이블 행을 파싱한다.
2. 이번에 처리한 결과를 기존 테이블에 추가한다 (기존 행 유지 + 새 행 추가).
3. 요약 통계(전체/반영/거절 수)를 합산하여 갱신한다.
4. 병합된 내용으로 새 코멘트를 게시한다.
5. 기존 코멘트를 삭제한다.

```bash
# 기존 코멘트 삭제
gh api repos/{OWNER}/{REPO}/issues/comments/{COMMENT_ID} -X DELETE
```

#### 6-2b. 기존 코멘트가 없는 경우

이번 결과만으로 새 코멘트를 게시한다.

#### 코멘트 형식

```bash
gh pr comment {PR_NUMBER} --body "$(cat <<'EOF'
## 🤖 CodeRabbit Review Resolution

**Total unresolved**: {N} | **Applied**: {X} | **Declined**: {Y}

### ✅ Applied
| Severity | File | Comment | Change | Commit |
|:--------:|------|---------|--------|--------|
| {emoji} | `{path}` | {CodeRabbit summary} ([link](https://github.com/{OWNER}/{REPO}/pull/{PR_NUMBER}#discussion_r{COMMENT_DATABASE_ID})) | {change summary} | [{short_hash}](https://github.com/{OWNER}/{REPO}/commit/{full_hash}) |

### ❌ Declined
| Severity | File | Comment | Reason |
|:--------:|------|---------|--------|
| {emoji} | `{path}` | {CodeRabbit comment summary} ([link](https://github.com/{OWNER}/{REPO}/pull/{PR_NUMBER}#discussion_r{COMMENT_DATABASE_ID})) | {decline reason summary} |
EOF
)"
```

### 7단계: 사용자에게 최종 요약 출력

모든 코멘트 처리 후 아래 형식으로 요약을 출력한다.

**기본 출력 (코드레벨 상세 포함)**:

```text
## CodeRabbit 리뷰 반영 결과

- PR: #{PR_NUMBER}
- 전체 미해결 코멘트: {N}개
- 반영: {X}개
- 거절: {Y}개

### 반영 내역
| 중요도 | 파일 | 내용 | 커밋 |
|:------:|------|------|------|
| {emoji} | {path} | {요약} ([링크](https://github.com/{OWNER}/{REPO}/pull/{PR_NUMBER}#discussion_r{COMMENT_DATABASE_ID})) | [{short_hash}](https://github.com/{OWNER}/{REPO}/commit/{full_hash}) |

#### 코드 변경 상세

**{path}** ([{short_hash}](https://github.com/{OWNER}/{REPO}/commit/{full_hash}))
- CodeRabbit 지적: {원래 지적 내용 요약}
- 변경 전: `{변경 전 코드 또는 설명}`
- 변경 후: `{변경 후 코드 또는 설명}`
- 변경 이유: {왜 이렇게 수정했는지 설명}

### 거절 내역
| 중요도 | 파일 | 내용 | 사유 |
|:------:|------|------|------|
| {emoji} | {path} | {요약} ([링크](https://github.com/{OWNER}/{REPO}/pull/{PR_NUMBER}#discussion_r{COMMENT_DATABASE_ID})) | {사유 요약} |
```

**`--brief` 옵션 사용 시**: "코드 변경 상세" 섹션을 생략하고 테이블만 출력한다.

### 8단계: 패턴 학습

모든 코멘트 처리가 끝난 후, **이번에 처리한 코멘트들이 기존에 반복된 패턴인지 확인**한다.

1. `learned-patterns.md` 파일을 읽는다.
2. 이번에 처리한 각 코멘트의 유형을 기존 패턴과 비교한다.
   - 같은 유형이면 횟수를 +1하고 PR 번호를 추가한다.
   - 새로운 유형이면 새 행을 추가한다.
3. `learned-patterns.md`를 업데이트한다.

#### 패턴 유형 분류 기준

코멘트의 구체적인 파일/라인이 아닌, **지적의 본질적 유형**으로 분류한다.

예시:
- "마크다운 코드 블록 언어 지정 누락 (MD040)" — 여러 파일에서 같은 지적
- "nullable 타입 에러 처리 누락" — 다른 파일이지만 같은 패턴
- "unused import 제거" — 반복되는 코드 품질 지적

#### SKILL.md 판단 기준 반영 제안

`learned-patterns.md` 업데이트 후, 반영 또는 거절 패턴의 횟수가 **3회 이상**인 항목이 있는지 확인한다.

해당 항목이 있으면, 사용자에게 아래와 같이 제안한다:

```text
## 반복 패턴 감지 — SKILL.md 업데이트 제안

아래 패턴이 반복되고 있어, SKILL.md의 판단 기준에 추가하면 향후 처리가 일관될 것으로 보입니다.

### 반영 기준 추가 제안
- "마크다운 코드 블록 언어 지정 누락" (3회 반복, 모두 반영)
  → 4-3 반영 기준에 "마크다운 린트 경고 (MD040 등)" 항목 추가

### 거절 기준 추가 제안
- (해당 없음)

SKILL.md에 반영할까요?
```

사용자가 승인하면:
1. SKILL.md의 4-3 섹션(반영/거절 기준)에 해당 패턴을 추가한다.
2. `learned-patterns.md`에서 **반영된 패턴 행을 삭제**한다 (이미 SKILL.md에 반영되었으므로, 카운트가 새로 쌓이도록 초기화).
3. 변경된 SKILL.md와 learned-patterns.md를 커밋한다.

사용자가 거절하면 `learned-patterns.md` 업데이트만 수행하고 넘어간다 (패턴 행은 유지).

#### learned-patterns.md 변경사항 커밋

8단계에서 `learned-patterns.md`가 변경된 경우, 자동으로 커밋한다.

```bash
git add .claude/skills/resolve-coderabbit-review/learned-patterns.md
git commit -m "chore: update learned-patterns.md"
```

SKILL.md 반영 제안이 승인되어 SKILL.md도 함께 변경된 경우, 하나의 커밋으로 묶는다.

```bash
git add .claude/skills/resolve-coderabbit-review/learned-patterns.md \
       .claude/skills/resolve-coderabbit-review/SKILL.md
git commit -m "chore: update review patterns and SKILL.md criteria"
```

## 주의사항

- **커밋 규칙**: 커밋 메시지에 `Co-Authored-By: Claude` 등 Claude/AI 관련 문구 금지, `--no-verify` 금지 (프로젝트 CLAUDE.md 참고)
- 코드 수정은 최소한의 변경만 수행한다 (CodeRabbit이 지적한 부분만)
- 수정 시 기존 코드 스타일과 패턴을 따른다
- **빌드는 실행하지 않는다** (사용자가 직접 수행)
- **반영 후 해당 리뷰 스레드를 resolve 처리한다** (수동 resolve)
- **거절 시 반드시 사용자 확인을 먼저 받는다** — 거절 대댓글을 바로 작성하지 않고, 사용자에게 거절 사유를 먼저 보여주고 승인받은 후 처리한다
