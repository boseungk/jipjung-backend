# Git Convention

## Commit Message Convention

| Type | Description |
|---|---|
| `feat` | 새로운 기능 추가 (New feature) |
| `fix` | 버그 수정 (Bug fix) |
| `docs` | 문서 수정 (Documentation changes) |
| `style` | 코드 포맷팅, 세미콜론 누락 등 (Formatting, missing semi-colons, etc.) |
| `refactor` | 코드 리팩토링 (Code refactoring) |
| `test` | 테스트 코드 추가 또는 수정 (Adding or fixing tests) |
| `chore` | 빌드 업무 수정, 패키지 매니저 수정 (Build tasks, package manager configs) |
| `design` | CSS 등 사용자 UI 디자인 변경 |
| `comment` | 필요한 주석 추가 및 변경 |
| `rename` | 파일 혹은 폴더명을 수정한 경우 |
| `remove` | 파일을 삭제만 한 경우 |
| `ci` | CI 관련 설정 변경 |
| `perf` | 성능 개선 |

### Subject

- 제목은 50자를 넘기지 않습니다.
- 마지막에 마침표(.)를 붙이지 않습니다.
- 과거 시제가 아닌 현재 시제로 작성합니다.
- "Fixes", "Adds", "Changes" 같은 접두사 대신 커밋 타입을 사용합니다.

### Body

- 본문은 선택 사항이며, 변경 사항에 대한 자세한 설명을 제공합니다.
- 한 줄에 72자를 넘지 않도록 합니다.
- 어떻게(How)보다는 무엇(What)과 왜(Why)를 설명합니다.

### Footer

- 관련된 이슈 번호를 포함할 때 사용합니다. (e.g., `Closes #123`, `Fixes #456`)
