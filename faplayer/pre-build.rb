#!/usr/bin/env ruby

abi = `cat jni/Application.mk | grep ^APP_ABI | cut -d' ' -f3`.strip!
flag = `cat jni/Application.mk | grep "^OPT_CFLAGS +="`.split("\n")
no_neon = abi != 'armeabi-v7a'
if abi == 'armeabi-v7a'
    flag = flag[0]
    temp = flag.scan(/-mfpu=([^\s]+)/)
    fpu = temp[0][0].to_s
    no_neon = true if fpu != 'neon'
end
all = Array.new
list = `find . -name Android.mk`.split("\n")
list.each { |l|
    next if ((l =~ /^\.\/jni\/vlc\/modules/) == nil)
    temp = l.scan(/modules\/([^\/]+)\//)
    next if temp == nil || temp.size == 0
    File.open(l) { |f|
        while !f.eof?
            ln = f.readline
            next if ((ln =~ /^LOCAL_MODULE/) == nil)
            temp = ln.scan(/\s*LOCAL_MODULE\s*:=\s*([^\s]+)/)
            next if temp == nil || temp.size == 0
            name = temp[0][0].to_s
            name = name[3..-1] if (name =~ /^lib/) != nil
            next if no_neon && (name =~ /_neon_plugin$/) != nil
            all.push(name)
        end
    }
}
all.sort!
f = File.open('jni/vlc/src/libvlcjni.h', 'w') { |f|
    f.write("/* auto generated */\n")
    all.each { |m|
        a = m.sub(/_plugin$/, '')
        f.write("vlc_declare_plugin(#{a});\n")
    }
    f.write("const void *vlc_builtins_modules[] = {\n");
    all.each { |m|
        a = m.sub(/_plugin$/, '')
        f.write("\tvlc_plugin(#{a}),\n");
    }
    f.write("\tNULL\n");
    f.write("};\n");
    f.write("/* auto generated */\n")
}
n = `grep -n '# modules' jni/vlc/Android.mk | cut -d: -f1`
n = n.to_i + 1
old = `sed -n #{n}p jni/vlc/Android.mk`.strip!
new = 'LOCAL_STATIC_LIBRARIES += ' + all.join(' ')
if old != new
    `sed -i "#{n} c\\#{new}" jni/vlc/Android.mk`
end

