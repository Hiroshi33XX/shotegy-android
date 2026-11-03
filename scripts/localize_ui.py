# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 Hiroshi33XX and contributors.
import json,re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/com/shotegy/android'
keys=set()

def string_end(s,i):
    if s.startswith('"""',i):
        n=s.find('"""',i+3);return len(s) if n<0 else n+3
    j=i+1
    while j<len(s):
        if s[j]=='\\':j+=2;continue
        if s.startswith('${',j):j=expression_end(s,j+2)+1;continue
        if s[j]=='"':return j+1
        j+=1
    return j

def expression_end(s,i):
    depth=1;j=i
    while j<len(s):
        if s[j]=='"':j=string_end(s,j);continue
        if s[j]=='{':depth+=1
        elif s[j]=='}':
            depth-=1
            if depth==0:return j
        j+=1
    raise ValueError('Unclosed Kotlin interpolation')

def literal(s):
    out='';args=[];i=1
    while i<len(s)-1:
        if s[i]=='\\':
            code=s[i+1];out+= {'n':'\n','r':'\r','t':'\t','b':'\b','f':'\f','"':'"','\\':'\\','$':'$'}.get(code,code);i+=2
        elif s.startswith('${',i):
            end=expression_end(s,i+2);out+='{'+str(len(args))+'}';args.append(s[i+2:end]);i=end+1
        elif s[i]=='$' and re.match(r'[A-Za-z_]',s[i+1:i+2]):
            m=re.match(r'\$([A-Za-z_][A-Za-z_0-9]*)',s[i:]);out+='{'+str(len(args))+'}';args.append(m[1]);i+=len(m[0])
        else:out+=s[i];i+=1
    return out,args

def tokens(s,start=0,end=None):
    end=end or len(s);i=start
    while i<end:
        if s.startswith('//',i):
            i=s.find('\n',i)
            if i<0:return
        elif s.startswith('/*',i):
            n=s.find('*/',i+2);i=end if n<0 else n+2
        elif s[i]=='"':
            j=string_end(s,i);yield i,j,s[i:j];i=j
        else:i+=1

def arguments(s,start):
    # Return top-level argument ranges without interpreting lambda bodies.
    ranges=[];depth=0;a=start;i=start
    while i<len(s):
        if s[i]=='"':i=string_end(s,i);continue
        c=s[i]
        if c in '([{':depth+=1
        elif c in ')]}':
            if c==')' and depth==0:ranges.append((a,i));return ranges
            depth-=1
        elif c==',' and depth==0:ranges.append((a,i));a=i+1
        i+=1
    return ranges

def transform():
    for p in source.glob('*.kt'):
        if p.name in ['Localization.kt','ProjectExportService.kt','IOSBackup.kt','Store.kt','ImagePdfWriter.kt','CaptureTelemetry.kt','MediaImages.kt','StructuredMedia.kt','ProjectTemplates.kt']:continue
        s=p.read_text(encoding='utf-8');ranges=[];protected=[(a,b) for a,b,_ in tokens(s)]
        for m in re.finditer(r'\b(Text|Icon|MainPageHeader|NavigationHeader)\s*\(',s):
            if any(a<=m.start()<b for a,b in protected):continue
            args=arguments(s,m.end());which=1 if m[1]=='Icon' else 0
            if len(args)<=which:continue
            ranges.append(args[which])
        replacements={}
        for a,b in ranges:
            for x,y,t in tokens(s,a,b):
                if not re.search('[\u3400-\u9fff]',t) or t.startswith('"""'):continue
                # Avoid rewriting literals already passed to a localization helper.
                if re.search(r'(?:\btr(?:Message)?\s*\(\s*|==\s*|!=\s*)$',s[max(0,x-20):x]):continue
                key,values=literal(t)
                if not re.search('[\u3400-\u9fff]',key):continue
                keys.add(key)
                arguments_text=''.join(', '+v for v in values)
                replacements[(x,y)]='tr('+json.dumps(key,ensure_ascii=False)+arguments_text+')'
        for (a,b),v in sorted(replacements.items(),reverse=True):s=s[:a]+v+s[b:]
        p.write_text(s,encoding='utf-8')
    (root/'build/ui-translation-keys.json').write_text(json.dumps(sorted(keys),ensure_ascii=False,indent=2),encoding='utf-8')

if __name__=='__main__':transform()
