import json,sys,subprocess
d=json.loads(subprocess.run(['xcrun','xcresulttool','get','test-results','tests','--path',sys.argv[1]],capture_output=True,text=True).stdout)
p=f=0
def walk(n):
    global p,f
    if n.get('nodeType')=='Test Case':
        r=n.get('result'); p+=r in ('Passed','Expected Failure'); f+=r not in ('Passed','Expected Failure','Skipped')
        print(r, n.get('name'), n.get('duration',''))
        for c in n.get('children',[]):
            if c.get('nodeType')=='Failure Message': print('    ', c.get('name','')[:400].replace('\n',' '))
    for c in n.get('children',[]): walk(c)
for t in d['testNodes']: walk(t)
print(f'passed {p} failed {f}')
