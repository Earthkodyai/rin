# Network bytes Android charges to one app uid, per 2-hour bucket (6.3): python tools/uidnet.py <uid>. Run `adb shell dumpsys netstats --poll` first.
import re,subprocess,sys,os,time
adb=os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
out=subprocess.run([adb,'shell','dumpsys','netstats','detail'],capture_output=True,text=True,encoding='utf-8',errors='replace').stdout
# only the per-uid section ("UID stats"), tag 0x0 = totals per uid/set
tot={}
cur=None
for line in out.splitlines():
    m=re.search(r'uid=(\d+) set=(\w+) tag=(0x[0-9a-f]+)',line)
    if 'ident=' in line:
        cur = (m.group(1),m.group(2),m.group(3)) if m else None
        continue
    b=re.search(r'st=(\d+) rb=(\d+) rp=(\d+) tb=(\d+) tp=(\d+)',line)
    if b and cur and cur[0]==sys.argv[1]:
        k=cur
        tot.setdefault(k,{})[int(b[1])]=(int(b[2]),int(b[4]))
for k,v in sorted(tot.items()):
    print(k, {time.strftime('%H:%M',time.localtime(s)):x for s,x in sorted(v.items())})
