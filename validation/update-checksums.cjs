const {execFileSync}=require('node:child_process');
const {writeFileSync}=require('node:fs');
const {createHash}=require('node:crypto');
// Hash staged Git blobs, independent of Windows working-copy line endings.
const paths=execFileSync('git',['ls-files','-z'],{encoding:'utf8'}).split('\0').filter(Boolean).filter(p=>p!=='SHA256SUMS.txt').sort();
const lines=paths.map(path=>{
    const bytes=execFileSync('git',['show',':'+path],{maxBuffer:20*1024*1024});
    return createHash('sha256').update(bytes).digest('hex')+'  '+path;
});
writeFileSync('SHA256SUMS.txt',lines.join('\n')+'\n');
console.log('Updated '+lines.length+' canonical Git blob checksums.');
