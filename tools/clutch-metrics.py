import re,sys
rows=[]
for l in open(sys.argv[1],errors='ignore'):
    m=re.search(r'CLUTCHTRACE x=(\S+) y=(\S+) z=(\S+) g=(\w+) v=(\S+) yaw=(\S+) pit=(\S+) travel=(\S+) keys=(\S*) click=(\w+) aim=(\w+) placed=(\d+)',l)
    if m: rows.append(m.groups())
n=len(rows)
if not n: print("no trace"); sys.exit()
x=[float(r[0]) for r in rows]; y=[float(r[1]) for r in rows]
yaw=[float(r[5]) for r in rows]
def d(a,b):
    q=(a-b+180)%360-180; return abs(q)
dy=[d(yaw[i],yaw[i-1]) for i in range(1,n)]
dp=[abs(float(rows[i][6])-float(rows[i-1][6])) for i in range(1,n)]
v=[float(r[4]) for r in rows]
print("ticks",n,"dx",round(x[-1]-x[0],1),"minY",min(y),"placed",rows[-1][11])
print("mean v %.3f  mean|dyaw| %.1f  ticks dyaw>10: %d  dpitch>10: %d"%(sum(v)/n,sum(dy)/len(dy),sum(1 for a in dy if a>10),sum(1 for a in dp if a>10)))
print("jumps",sum(1 for r in rows if 'J' in r[8]),"stand-still ticks(keys empty & grounded)",sum(1 for r in rows if r[8]=='' and r[3]=='true'))
