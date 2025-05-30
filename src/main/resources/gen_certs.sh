#!/usr/bin/bash

certs="certs"
rm -rf ${certs}
echo "mkdir ${certs}"
mkdir ${certs}
cd ${certs}

#echo "Generate X.509 version 3 extensions for CA"
#cat > ca.ext << EOF
#subjectKeyIdentifier=hash
#authorityKeyIdentifier=keyid,issuer
#basicConstraints=critical,CA:TRUE
#keyUsage=critical,digitalSignature,keyCertSign,cRLSign
#EOF

echo "Generate X.509 version 3 extensions for sign + enc EE"
cat > ee.ext << EOF
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid,issuer
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment,dataEncipherment,keyAgreement
EOF

OPENSSL=openssl

#$OPENSSL genpkey -algorithm ec -pkeyopt ec_paramgen_curve:SM2 -pkeyopt ec_param_enc:named_curve -out root-ca.key
#$OPENSSL req -new -key root-ca.key -subj "/CN=self-signed-root-ca" -sm3 -out root-ca.csr
#$OPENSSL x509 -extfile ca.ext -req -days 3650 -in root-ca.csr -sm3 \
#    -signkey root-ca.key -out root-ca.crt.tmp
#$OPENSSL x509 -text -in root-ca.crt.tmp > root-ca.crt
#
## another ca is needed, otherwise certificate verify would fail somehow
#$OPENSSL genpkey -algorithm ec -pkeyopt ec_paramgen_curve:SM2 -pkeyopt ec_param_enc:named_curve -out ca.key
#$OPENSSL req -new -key ca.key -subj "/CN=ca" -sm3 -out ca.csr
#$OPENSSL x509 -extfile ca.ext -req -CAcreateserial -days 3650 -in ca.csr -sm3 \
#    -CA root-ca.crt -CAkey root-ca.key -out ca.crt.tmp
#$OPENSSL x509 -text -in ca.crt.tmp > ca.crt

# generate self signed certificates

$OPENSSL genpkey -algorithm ec -pkeyopt ec_paramgen_curve:SM2 -pkeyopt ec_param_enc:named_curve -out server.key
$OPENSSL req -new -key server.key -subj "/CN=server" -sm3 -out server.csr
$OPENSSL x509 -extfile ee.ext -req -days 3650 -in server.csr -sm3 \
    -signkey server.key -out server.crt.tmp
$OPENSSL x509 -text -in server.crt.tmp > server.crt

$OPENSSL genpkey -algorithm ec -pkeyopt ec_paramgen_curve:SM2 -pkeyopt ec_param_enc:named_curve -out client.key
$OPENSSL req -new -key client.key -subj "/CN=client" -sm3 -out client.csr
$OPENSSL x509 -extfile ee.ext -req -days 3650 -in client.csr -sm3 \
    -signkey client.key -out client.crt.tmp
$OPENSSL x509 -text -in client.crt.tmp > client.crt

rm *.tmp *.csr
