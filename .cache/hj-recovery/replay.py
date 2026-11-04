#!/usr/bin/env python3
"""Replay the file-mutating tool calls of the huanjing authoring session
into a scratch directory, so the final huanjing-live7 package can be rebuilt."""
import json, os, re, shutil, subprocess, sys

LOG = '/Users/xiaoxu/PycharmProjects/Java/.cache/hj-recovery/session-7592dc91-1041-414a-91c0-76bee437a73e.jsonl'
ORIG = '/Users/xiaoxu/PycharmProjects/PythonProject/dsh-plugins'
SCRATCH = '/Users/xiaoxu/PycharmProjects/Java/.cache/hj-recovery/replay'

SKIP_CMD = re.compile(r'''(?:(?:^|\n)|(?:&&|;|\|)\s*)(?:open|pkill|kill|killall|launchctl|osascript|curl|pnpm|npm|npx|dsh|lsof|netstat|scutil|say|afplay)\b''')
MUTATE = ('cp -R', 'rm -rf', 'rm -f', 'mkdir -p', 'python3 - <<', 'cat >', 'sed -i', 'mv ', 'touch ')


def failed(text):
    if not text:
        return False
    if re.search(r'\[exit code: [1-9]', text):
        return True
    if 'Traceback (most recent call last)' in text:
        return True
    if text.startswith('Error:'):
        return True
    return False


def main():
    calls, results, order = {}, {}, []
    with open(LOG, encoding='utf-8', errors='replace') as fh:
        for ln in fh:
            o = json.loads(ln)
            if o.get('type') == 'tool/call':
                d = o['data']
                calls[d['callId']] = d
                order.append(d['callId'])
            elif o.get('type') == 'tool/result':
                m = o['data']['message']
                results[m.get('toolCallId')] = ''.join(
                    c.get('text', '') for c in m.get('content', []) if isinstance(c, dict))

    if os.path.exists(SCRATCH):
        shutil.rmtree(SCRATCH)
    os.makedirs(SCRATCH, exist_ok=True)

    stats = {'write': 0, 'edit': 0, 'bash': 0, 'skip': 0, 'fail': 0, 'mismatch': 0}
    problems = []

    for idx, cid in enumerate(order):
        d = calls[cid]
        name = d['name']
        if name not in ('write', 'edit', 'bash'):
            continue
        bad = failed(results.get(cid, ''))
        args = json.loads(d.get('arguments') or '{}')

        if name in ('write', 'edit'):
            path = args.get('file_path', '')
            if not path.startswith(ORIG):
                continue
            if bad:
                stats['skip'] += 1
                continue
            path = path.replace(ORIG, SCRATCH, 1)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            if name == 'write':
                with open(path, 'w', encoding='utf-8') as fh:
                    fh.write(args.get('content', ''))
                stats['write'] += 1
            else:
                with open(path, encoding='utf-8') as fh:
                    s = fh.read()
                old = args.get('old_string', '')
                new = args.get('new_string', '')
                if old not in s:
                    stats['mismatch'] += 1
                    problems.append(f'edit mismatch seq#{idx} {path}')
                    continue
                if args.get('replace_all'):
                    s = s.replace(old, new)
                else:
                    s = s.replace(old, new, 1)
                with open(path, 'w', encoding='utf-8') as fh:
                    fh.write(s)
                stats['edit'] += 1
            continue

        cmd = args.get('command', '')
        if 'dsh-plugins/' not in cmd or not any(k in cmd for k in MUTATE):
            continue
        if bad:
            stats['skip'] += 1
            continue
        if SKIP_CMD.search(cmd):
            stats['skip'] += 1
            continue
        cmd2 = cmd.replace(ORIG, SCRATCH)
        env = dict(os.environ)
        env['PYTHONDONTWRITEBYTECODE'] = '1'
        try:
            r = subprocess.run(['bash', '-c', cmd2], cwd=SCRATCH, env=env,
                               capture_output=True, text=True, timeout=180)
            code = r.returncode
            out = (r.stdout + r.stderr).strip()
        except subprocess.TimeoutExpired:
            code, out = 124, 'timeout'
        stats['bash'] += 1
        if code != 0:
            stats['fail'] += 1
            problems.append(f'bash exit {code} seq#{idx}: {cmd[:120]!r} -> {out[:300]!r}')

    root = os.path.join(SCRATCH, 'huanjing-live7')
    print('stats', stats)
    for p in problems:
        print('PROBLEM', p)
    if os.path.isdir(root):
        for dirpath, dirnames, filenames in os.walk(root):
            for f in sorted(filenames):
                fp = os.path.join(dirpath, f)
                print(f'{os.path.getsize(fp):>9}  {os.path.relpath(fp, root)}')
    else:
        print('MISSING', root)
        sys.exit(2)


if __name__ == '__main__':
    main()
