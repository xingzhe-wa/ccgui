const fs = require('fs');
const content = fs.readFileSync('webview/src/shared/stores/__tests__/appStore.test.ts', 'utf-8');

// Fix: add isInitialized: true before closing } of const mockSession: ChatSession = {...};
// Pattern: 'status: SessionStatus.IDLE,\n        };' -> add '          isInitialized: true,\n        };'

// Also fix: 'status: SessionStatus.IDLE,\n          },\n          {\n            id: '2',' -> these were already fixed by earlier replace_all

// Let me just find all 'status: SessionStatus.IDLE,' that are followed by '};' (not '},')
const lines = content.split('\r\n');
const result = [];
let i = 0;
while (i < lines.length) {
  const line = lines[i];
  // Check if this is 'status: SessionStatus.IDLE,' followed by '};'
  if (line.includes('status: SessionStatus.IDLE,')) {
    const nextLine = lines[i+1] || '';
    const prevLine = lines[i-1] || '';
    // If next line is '        };' (closing a const mockSession)
    // But we also need to check the context - it shouldn't already have isInitialized
    if (nextLine.trim() === '};' && !prevLine.includes('isInitialized')) {
      result.push(line);
      result.push(nextLine.replace('};', ',\n          isInitialized: true,\n        };'));
      i += 2;
      continue;
    }
    // If next line is '          },' (array item) or '},' - already handled
    if (nextLine.trim() === '},' || nextLine.trim() === '},') {
      result.push(line);
      result.push(nextLine);
      i += 2;
      continue;
    }
  }
  result.push(line);
  i++;
}

console.log('Processed', i, 'lines');
console.log('Added isInitialized to', result.filter(l => l.includes('isInitialized: true')).length, 'places');

fs.writeFileSync('webview/src/shared/stores/__tests__/appStore.test.ts', result.join('\r\n'));
