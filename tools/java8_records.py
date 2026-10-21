# Record lowering shared in design with OpenUI MC. No Minecraft source is copied.
import re
def end_pair(text, start, opening='(', closing=')'):
    depth=0; quote=None; escaped=False; comment=None; i=start
    while i<len(text):
        ch=text[i]; nxt=text[i:i+2]
        if comment:
            if comment=='line' and ch=='\n': comment=None
            elif comment=='block' and nxt=='*/': comment=None; i+=1
        elif quote:
            if escaped: escaped=False
            elif ch=='\\': escaped=True
            elif ch==quote: quote=None
        elif nxt=='//': comment='line'; i+=1
        elif nxt=='/*': comment='block'; i+=1
        elif ch in '\"\'': quote=ch
        elif ch==opening: depth+=1
        elif ch==closing:
            depth-=1
            if not depth:return i
        i+=1
    raise ValueError('Unbalanced source near '+text[start:start+80])

def split_params(params):
    result=[]; start=0; depth=0
    for i,ch in enumerate(params):
        if ch in '<([':depth+=1
        elif ch in '>)]':depth-=1
        elif ch==',' and depth==0: result.append(params[start:i].strip());start=i+1
    if params[start:].strip(): result.append(params[start:].strip())
    return result

def surface(text):
    out=[];i=0
    while i<len(text):
        if text[i]=='{':
            i=end_pair(text,i,'{','}')+1;out.append('{}')
        else:out.append(text[i]);i+=1
    return ''.join(out)

def lower_records(text, nested_context=False):
    while True:
        match=re.search(r'(?P<access>public|protected|private)?\s*(?P<static>static\s+)?\brecord\s+(?P<name>\w+)(?P<generic><[^{}()]+>)?\s*\(',text)
        if not match:return text
        start=match.end()-1; end=end_pair(text,start); body_start=text.index('{',end); body_end=end_pair(text,body_start,'{','}')
        name=match['name']; generic=match['generic'] or ''; params=split_params(text[start+1:end]); fields=[]
        for param in params:
            typ,field=param.rsplit(None,1);fields.append((typ,field))
        signature=', '.join(params); assignments=' '.join('this.%s=%s;'%(f,f) for t,f in fields)
        declared=surface(text[body_start+1:body_end]); body=lower_records(text[body_start+1:body_end], True); compact=re.search(r'\b(public|private|protected)?\s*'+name+r'\s*\{',body)
        has_canonical=False
        if compact:
            cb=body.index('{',compact.start());ce=end_pair(body,cb,'{','}')
            body=body[:compact.start()]+'public '+name+'('+signature+') {'+body[cb+1:ce]+assignments+'}'+body[ce+1:]
            has_canonical=True
        else:
            for ctor in re.finditer(r'\b'+name+r'\s*\(',body):
                ce=end_pair(body,ctor.end()-1)
                existing=split_params(body[ctor.end():ce])
                if existing==params:has_canonical=True
        nested=nested_context or text[:match.start()].count('{')>text[:match.start()].count('}')
        prefix=(match['access']+' ' if match['access'] else '')+('static ' if nested else '')+'final class '+name+generic+text[end+1:body_start]+' {'
        generated='\n'+''.join('private final %s %s;\n'%(t,f) for t,f in fields)
        if not has_canonical:generated+='public '+name+'('+signature+') {'+assignments+'}\n'
        for typ,field in fields:
            if not re.search(r'public\s+[^;{}\n]+\b'+field+r'\s*\(\s*\)',declared):generated+='public '+typ+' '+field+'() {return '+field+';}\n'
        if not re.search(r'boolean\s+equals\(',declared):
            generated+='@Override public boolean equals(Object __other) {if(this==__other)return true;if(!(__other instanceof '+name+'))return false;'+name+' __v=('+name+')__other;return '+(' && '.join('java.util.Objects.equals(this.%s,__v.%s)'%(f,f) for t,f in fields) or 'true')+';}\n'
        if not re.search(r'int\s+hashCode\(',declared):generated+='@Override public int hashCode(){int __h=0;'+''.join('__h=31*__h+java.util.Objects.hashCode(%s);'%f for t,f in fields)+'return __h;}\n'
        if not re.search(r'String\s+toString\(',declared):generated+='@Override public String toString(){return "'+name+'["+'+(' + ", "+'.join('"'+f+'="+String.valueOf('+f+')' for t,f in fields) or '""')+'+"]";}\n'
        text=text[:match.start()]+'\n'+prefix+generated+body+'}'+text[body_end+1:]
