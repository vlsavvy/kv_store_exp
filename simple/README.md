PS C:\Users\bvidh\IdeaProjects\untitled> @"
>> k1`t v1                                                                                                                                                                          
>> k2`t v2                                                                                                                                                                          
>> k3`t v3                                                                                                                                                                          
>> "@ | & curl.exe -X POST --data-binary "@-" "http://localhost:8080/batch"                                                                                                         
OK 3
PS C:\Users\bvidh\IdeaProjects\untitled> @"
>> k1`t v1                                                                                                                                                                          
>> k2`t v2                                                                                                                                                                          
>> k3`t v3                                                                                                                                                                          
>> "@ | & curl.exe -X POST --data-binary "@-" "http://localhost:8080/batch"                                                                                                         
OK 3
PS C:\Users\bvidh\IdeaProjects\untitled> curl.exe "http://localhost:8080/kv?key=k1"
v1
PS C:\Users\bvidh\IdeaProjects\untitled> curl.exe "http://localhost:8080/range?start=k1&end=k9"
k1       v1
k2       v2
k3       v3

## API support
PUT → /kv?key=...&value=...

GET → /kv?key=...

DELETE → /kv?key=...

BATCH PUT → /batch with tab-separated lines

RANGE SCAN → /range?start=...&end=...