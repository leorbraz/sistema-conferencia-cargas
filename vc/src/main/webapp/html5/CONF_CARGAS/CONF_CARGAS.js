angular.module('CONF_CARGASApp', ['snk'])
  .controller('CONF_CARGASController', ['$scope', function ($scope) {
    var self = this;
    self.versaoInterface = "1.0.60.1-doca-foco-fullscreen";
    self.perfilConferencia = null;
    self.perfilCarregado = false;
    self.ehSupervisor = function(){return self.perfilCarregado && self.perfilConferencia === 'S';};
    self.temPerfilConferencia = function(){return self.perfilCarregado && (self.perfilConferencia === 'S' || self.perfilConferencia === 'C');};
    self.carregarPerfilConferencia = function(){
      return self.requisicaoLeitura('consultarPerfilConferencia',{}).then(function(d){
        $scope.$applyAsync(function(){self.perfilConferencia=d.perfil;self.perfilCarregado=true;});
      }).catch(function(err){
        $scope.$applyAsync(function(){self.perfilConferencia=null;self.perfilCarregado=true;self.mensagemBusca='Não foi possível consultar o perfil da conferência: '+(err.message||err);});
      });
    };

    // v1.0.58: teste de tela cheia via gesto do operador (Chrome/Android).
    // Pode ser bloqueado quando a personalizacao esta em iframe sem permissao fullscreen.
    self.alternarTelaCheia = function () {
      var doc = document;
      var ativo = doc.fullscreenElement || doc.webkitFullscreenElement;
      var raiz = doc.documentElement;
      var operacao;
      try {
        if (ativo) {
          operacao = doc.exitFullscreen ? doc.exitFullscreen() :
            (doc.webkitExitFullscreen ? doc.webkitExitFullscreen() : null);
        } else {
          operacao = raiz.requestFullscreen ? raiz.requestFullscreen() :
            (raiz.webkitRequestFullscreen ? raiz.webkitRequestFullscreen() : null);
          if (!operacao && !(doc.fullscreenElement || doc.webkitFullscreenElement)) {
            self.mensagemBusca = 'Este navegador não disponibilizou a função de tela cheia nesta tela.';
          }
        }
        if (operacao && typeof operacao.catch === 'function') {
          operacao.catch(function () {
            $scope.$applyAsync(function () {
              self.mensagemBusca = 'O Chrome bloqueou a tela cheia. Pode ser uma restrição do Sankhya (iframe).';
            });
          });
        }
      } catch (e) {
        self.mensagemBusca = 'Não foi possível ativar a tela cheia neste navegador.';
      }
    };

    self.mobilePainel = ""; // Layout responsivo para coletor; validacao fisica da doca pendente de backend

    // Janela unificada: nao utiliza alert/confirm/prompt do navegador.
    self.dialogo=null;
    self.abrirDialogo=function(opcoes){
      return new Promise(function(resolve){
        $scope.$applyAsync(function(){
          self.dialogo={titulo:opcoes.titulo||'Confirmacao',mensagem:opcoes.mensagem||'',
            tipo:opcoes.tipo||'confirmacao',rotulo:opcoes.rotulo||'Confirmar',rotuloCancelar:opcoes.rotuloCancelar||'Cancelar',somenteAviso:!!opcoes.somenteAviso,
            exigeJustificativa:!!opcoes.exigeJustificativa,justificativa:'',erro:'',resolver:resolve};
        });
      });
    };
    self.responderDialogo=function(confirmado){
      var d=self.dialogo;if(!d)return;
      if(confirmado && d.exigeJustificativa){
        var motivo=(d.justificativa||'').trim();
        if(motivo.length<5){d.erro='Informe uma justificativa com pelo menos 5 caracteres.';return;}
        if(motivo.length>500){d.erro='Justificativa limitada a 500 caracteres.';return;}
      }
      self.dialogo=null;
      d.resolver(confirmado ? (d.exigeJustificativa ? d.justificativa.trim() : true) : null);
    };
    self.eventosHistorico=[];
    self.mensagemHistorico='';
    self.consultarHistorico=function(){
      if(!self.ehSupervisor()){self.eventosHistorico=[];self.mensagemHistorico='';return;}
      if(!self.numeroConferencia){self.eventosHistorico=[];self.mensagemHistorico='Nenhuma conferencia selecionada.';return;}
      var n=Number(self.numeroConferencia);
      self.mensagemHistorico='Carregando historico...';
      self.requisicaoLeitura('consultarHistorico',{NUCONF:n})
        .then(function(result){$scope.$applyAsync(function(){if(Number(self.numeroConferencia)!==n)return;self.eventosHistorico=result.eventos||[];self.mensagemHistorico=self.eventosHistorico.length?'':'Nenhum evento registrado.';});})
        .catch(function(err){$scope.$applyAsync(function(){self.mensagemHistorico='Erro: '+err.message;});});
    };
    self.linhaSelecionada=null;
    self.modalExclusao=false;
    self.leiturasIndividuais=[];
    self.leituraExcluir=null;
    self.motivoExclusao='';
    self.mensagemExclusao='';
    self.excluindoLeitura=false;
    self.requisicaoLeitura = function(servico,payload) {
      var url=new URL('service.sbr',window.location.href);
      url.searchParams.set('serviceName','ConferenciaCargaSP.'+servico);
      url.searchParams.set('outputType','json');
      Object.keys(payload).forEach(function(k){url.searchParams.set(k,String(payload[k]));});
      var session=new URLSearchParams(window.location.search).get('mgeSession');
      if(session)url.searchParams.set('mgeSession',session);
      return fetch(url.toString(),{method:'POST',credentials:'same-origin',headers:{'Accept':'application/json','Content-Type':'application/json'},body:JSON.stringify({serviceName:'ConferenciaCargaSP.'+servico,requestBody:payload})})
        .then(function(resp){return resp.text().then(function(body){if(!resp.ok)throw new Error('HTTP '+resp.status+': '+body.substring(0,200));return JSON.parse(body);});})
        .then(function(data){if(String(data.status)==='0'||data.status==='ERROR')throw new Error(data.statusMessage||'Falha na operacao.');var result=data.responseBody||{};if(typeof result==='string')result=JSON.parse(result);return result.responseBody||result;});
    };
    self.abrirExclusao=function(){
      if(!self.linhaSelecionada||!self.numeroConferencia)return;
      self.modalExclusao=true;self.mensagemExclusao='Carregando leituras...';
      self.leiturasIndividuais=[];self.leituraExcluir=null;self.motivoExclusao='';
      var l=self.linhaSelecionada;
      self.requisicaoLeitura('listarLeiturasIndividuais',{NUCONF:self.numeroConferencia,CODPROD:l.codprod,CONTROLE:l.lote,CODVOL:l.unidade})
        .then(function(result){$scope.$applyAsync(function(){self.leiturasIndividuais=result.leituras||[];self.mensagemExclusao=self.leiturasIndividuais.length?'':'Nenhuma leitura ativa encontrada.';});})
        .catch(function(err){$scope.$applyAsync(function(){self.mensagemExclusao='Erro: '+err.message;});});
    };
    self.fecharExclusao=function(){if(!self.excluindoLeitura)self.modalExclusao=false;};
    self.confirmarExclusao=function(){
      if(self.excluindoLeitura||!self.leituraExcluir||!self.motivoExclusao||!self.motivoExclusao.trim())return;
      var id=self.leituraExcluir.nuleit;
      self.abrirDialogo({titulo:'Confirmar exclusão',mensagem:'Excluir a leitura nº '+id+'? Esta ação será registrada no histórico.',tipo:'perigo',rotulo:'Excluir leitura'}).then(function(ok){
      if(!ok)return;
      self.excluindoLeitura=true;self.mensagemExclusao='Excluindo leitura...';
      self.requisicaoLeitura('excluirLeitura',{NUCONF:self.numeroConferencia,NULEIT:id,MOTIVO:self.motivoExclusao.trim()})
        .then(function(){$scope.$applyAsync(function(){self.modalExclusao=false;self.linhaSelecionada=null;self.mensagemPainel='Leitura nº '+id+' excluida. Historico registrado.';self.consultarPainel();});})
        .catch(function(err){$scope.$applyAsync(function(){self.mensagemExclusao='Erro: '+err.message;});})
        .finally(function(){$scope.$applyAsync(function(){self.excluindoLeitura=false;});});
      });
    };
    self.finalizarConferencia=function(){
      if(self.finalizandoConferencia || self.statusCodigo!=='A' || !self.numeroConferencia)return;
      var n=Number(self.numeroConferencia);
      self.finalizandoConferencia=true;
      self.mensagemFinalizacao='Verificando conferencia...';
      self.requisicaoLeitura('finalizarConferencia',{NUCONF:n,CONFIRMAR:'N',DIVERGENTE:'N'})
        .then(function(pre){
          if(Number(self.numeroConferencia)!==n)throw new Error('A conferencia selecionada mudou.');
          return self.abrirDialogo({
            titulo:pre.divergente?'Finalizar com divergência':'Finalizar conferência',
            mensagem:pre.divergente?'Foram identificadas divergências na conferência física. Deseja finalizar e encaminhar para validação administrativa? Após finalizar, novas leituras e exclusões serão bloqueadas.':'Deseja finalizar a conferência física e encaminhar para validação administrativa? Após finalizar, novas leituras e exclusões serão bloqueadas.',
            tipo:pre.divergente?'alerta':'confirmacao',rotulo:'Finalizar conferência'
          }).then(function(ok){
            if(!ok){$scope.$applyAsync(function(){self.mensagemFinalizacao='Finalização cancelada. A conferência permanece aberta.';});return null;}
            return self.requisicaoLeitura('finalizarConferencia',{NUCONF:n,CONFIRMAR:'S',DIVERGENTE:pre.divergente?'S':'N'});
          });
        })
        .then(function(result){if(!result)return;$scope.$applyAsync(function(){
          if(Number(self.numeroConferencia)!==n)return;
          self.statusCodigo=result.status;
          self.statusConferencia=result.status==='D'?'FINALIZADA DIVERGENTE':'FINALIZADA OK';
          self.mensagemFinalizacao=result.mensagem+' Aguardando validacao administrativa.';
          self.linhaSelecionada=null;
          self.consultarPainel();
          self.abrirDialogo({titulo:'Conferência finalizada!',mensagem:result.status==='D'?'Contagem finalizada com divergências. Aguarde a validação do Supervisor. A mercadoria ainda não está liberada para carregamento.':'Contagem concluída com sucesso. Aguarde a validação do Supervisor. A mercadoria ainda não está liberada para carregamento.',tipo:result.status==='D'?'alerta':'confirmacao',rotulo:'OK',somenteAviso:true})
            .then(function(){
              // Somente no coletor, apos o operador confirmar o aviso de sucesso.
              if(!self.ehLayoutColetor())return;
              $scope.$applyAsync(function(){
                self.voltarFila();
                self.filtroOcFila='';
                self.ordemCarga=null;
                self.numeroConferencia=null;
                self.doca=null;
                self.docaValidada=false;
                self.barraLeitura='';self.loteLeitura='';self.unidadeLeitura='';
                self.mensagemLeitura='';self.mensagemFinalizacao='';
              });
            });
        });})
        .catch(function(err){$scope.$applyAsync(function(){self.mensagemFinalizacao='Erro: '+err.message;});})
        .finally(function(){$scope.$applyAsync(function(){self.finalizandoConferencia=false;});});
    };
    self.acaoAdministrativaEmAndamento=false;
    self.mensagemAdministrativa='';
    self.decidirConferencia=function(acao){
      if(!self.ehSupervisor())return;
      if(self.acaoAdministrativaEmAndamento || !self.numeroConferencia || ['O','D'].indexOf(self.statusCodigo)<0)return;
      var n=Number(self.numeroConferencia);
      if(acao==='VALIDAR' && self.statusCodigo!=='O')return;
      if(acao==='VALIDAR_DIVERGENTE' && self.statusCodigo!=='D')return;
      var descricao=acao==='RECONFERIR'?'solicitar reconferência':(acao==='VALIDAR'?'validar conferência OK':'validar com divergências');
      self.abrirDialogo({titulo:acao==='RECONFERIR'?'Solicitar reconferência':'Decisão administrativa',mensagem:'Deseja '+descricao+'? Esta decisão será registrada no histórico.',
        exigeJustificativa:acao==='RECONFERIR'||acao==='VALIDAR_DIVERGENTE',
        rotulo:acao==='RECONFERIR'?'Solicitar reconferência':'Confirmar decisão'}).then(function(justificativa){
      if(justificativa===null)return;
      if(justificativa===true)justificativa='';
      self.acaoAdministrativaEmAndamento=true;
      self.mensagemAdministrativa='Processando decisao administrativa...';
      self.requisicaoLeitura('decidirConferencia',{NUCONF:n,ACAO:acao,JUSTIFICATIVA:justificativa})
        .then(function(r){$scope.$applyAsync(function(){
          if(Number(self.numeroConferencia)!==n)return;
          self.statusCodigo=r.status;
          self.statusConferencia=({'A':'EM ANDAMENTO / RECONFERENCIA','V':'VALIDADA','X':'VALIDADA COM DIVERGENCIA'})[r.status]||r.status;
          self.mensagemAdministrativa=r.mensagem;
          self.consultarConciliacao();self.consultarPainel();
        });})
        .catch(function(e){$scope.$applyAsync(function(){self.mensagemAdministrativa='Erro: '+e.message;});})
        .finally(function(){$scope.$applyAsync(function(){self.acaoAdministrativaEmAndamento=false;});});
      });
    };
    self.conciliacao=null;
    self.consultandoConciliacao=false;
    self.mensagemConciliacao='';
    self.filtroConciliacao='';
    self.consultarConciliacao=function(){
      if(!self.ehSupervisor())return;
      if(!self.numeroConferencia){self.mensagemConciliacao='Busque uma conferencia antes de conciliar.';return;}
      if(self.consultandoConciliacao)return;
      var n=Number(self.numeroConferencia);
      self.consultandoConciliacao=true;self.mensagemConciliacao='Consultando conciliacao...';
      self.requisicaoLeitura('consultarConciliacao',{NUCONF:n})
        .then(function(result){$scope.$applyAsync(function(){if(Number(self.numeroConferencia)!==n)return;self.conciliacao=result;self.mensagemConciliacao='Resultado atualizado.';});})
        .catch(function(err){$scope.$applyAsync(function(){self.mensagemConciliacao='Erro: '+err.message;});})
        .finally(function(){$scope.$applyAsync(function(){self.consultandoConciliacao=false;});});
    };
    self.preparandoInicio=false;
    self.ocLocalizadaParaInicio=false;
    self.podeIniciarConferencia=function(){return self.ehSupervisor() && self.ocLocalizadaParaInicio && !self.numeroConferencia && !self.resultadoCongelamento && !self.buscandoConferencia;};
    self.iniciarPelaBusca=function(){
      if(!self.podeIniciarConferencia() || self.preparandoInicio)return;
      var e=Number(self.empresa),o=Number(self.ordemCarga);
      self.preparandoInicio=true;
      self.mensagemBusca='Validando ordem de carga e composicao FIFO...';
      // Reutiliza as mesmas rotinas e validacoes da aba Preparacao/FIFO.
      self.requisicaoLeitura('consultarOC',{CODEMP:e,ORDEMCARGA:o})
        .then(function(oc){
          if(!oc.encontrada)throw new Error('OC nao encontrada.');
          if(Number(self.empresa)!==e || Number(self.ordemCarga)!==o)throw new Error('Empresa ou OC alterada. Busque novamente.');
          self.resultadoOC=oc;
          return self.requisicaoLeitura('consultarComposicao',{CODEMP:e,ORDEMCARGA:o});
        })
        .then(function(fifo){
          if(Number(self.empresa)!==e || Number(self.ordemCarga)!==o)throw new Error('Empresa ou OC alterada. Busque novamente.');
          self.resultadoFIFO=fifo;
          if(!fifo.alocacaoCompleta)throw new Error('Alocacao FIFO incompleta. Consulte Preparacao / FIFO.');
          $scope.$applyAsync(function(){self.mensagemBusca='OC e FIFO validados. Confirme o congelamento.';self.iniciarConferencia();});
        })
        .catch(function(err){$scope.$applyAsync(function(){self.mensagemBusca='Nao foi possivel iniciar: '+err.message;});})
        .finally(function(){$scope.$applyAsync(function(){self.preparandoInicio=false;});});
    };
    self.focarCodigoBarras=function(){
      // Sem deslocar a pagina: foco para o proximo scan.
      var el=document.getElementById('conf-codigo-barras');
      if(el && typeof el.focus==='function'){try{el.focus({preventScroll:true});}catch(e){el.focus();}}
    };
    self.nomeEmpresa = '';
    self.sugestoesEmpresa=[];self.focoNomeEmpresa=false;self.buscaNomeSeq=0;self.buscaCodigoSeq=0;
    self.mensagemBusca = '';
    self.pesquisaEmpresaAberta = false;
    self.empresasEncontradas = [];
    self.filtroEmpresa = '';
    self.buscandoConferencia = false;
    self.statusConferencia = '';
    self.statusCodigo = '';
    self.finalizandoConferencia = false;
    self.mensagemFinalizacao = '';
    // v1.0.34 - Fila de OCs; a abertura reutiliza o fluxo já validado.
    self.telaAtual='fila';
    self.filtroStatusFila='TODAS';
    self.filtroOcFila='';
    self.filtroDocaFila=null;
    self.filaOrdens=[];
    self.filaCarregando=false;
    // v1.0.37 - Grade compacta: colunas configuráveis e ordenação no cliente.
    self.filtrosFilaAbertos=true;
    self.configurarGradeFila=false;
    self.linhaFilaSelecionada=null;
    self.ordenacaoFila='ordem';
    self.ordemFilaDesc=false;
    self.colunasFila=[
      {id:'ordem',nome:'Nº OC',visivel:true},
      {id:'empresa',nome:'Empresa',visivel:true},
      {id:'nomeEmpresa',nome:'Nome da empresa',visivel:true},
      {id:'transportadora',nome:'Transportadora',visivel:true},
      {id:'doca',nome:'Doca',visivel:true},
      {id:'numeroConferencia',nome:'Nº conferência',visivel:true},
      {id:'status',nome:'Status conferência',visivel:true},
      {id:'administrativo',nome:'Situação administrativa',visivel:true}
    ];
    self.valorColunaFila=function(l,id){
      if(id==='status')return self.statusFilaLabel(l.status);
      if(id==='administrativo')return self.statusAdmFilaLabel(l.status);
      if(id==='numeroConferencia')return l.numeroConferencia||'—';
      return l[id]===undefined||l[id]===null?'—':l[id];
    };
    self.ordenarFila=function(id){
      if(self.ordenacaoFila===id)self.ordemFilaDesc=!self.ordemFilaDesc;
      else {self.ordenacaoFila=id;self.ordemFilaDesc=false;}
    };
    self.filaOrdenada=function(){
      var id=self.ordenacaoFila,desc=self.ordemFilaDesc,arr=self.filaFiltrada().slice();
      arr.sort(function(a,b){
        var av=self.valorColunaFila(a,id),bv=self.valorColunaFila(b,id);
        var an=Number(av),bn=Number(bv);
        var cmp=av!=='—'&&bv!=='—'&&!isNaN(an)&&!isNaN(bn)?an-bn:String(av).localeCompare(String(bv),'pt-BR',{sensitivity:'base',numeric:true});
        return desc?-cmp:cmp;
      });
      return arr;
    };
    self.selecionarLinhaFila=function(l){self.linhaFilaSelecionada=l;};
    self.quantidadeColunasFila=function(){return self.colunasFila.filter(function(c){return c.visivel;}).length;};
    self.restaurarGradeFila=function(){self.colunasFila.forEach(function(c){c.visivel=true;});self.ordenacaoFila='ordem';self.ordemFilaDesc=false;};

    self.filaMensagem='Informe os filtros e clique em Aplicar.';
    self.statusFilaLabel=function(c){return ({N:'Não iniciada',P:'Pendente',A:'Em andamento',O:'Finalizada OK',D:'Finalizada divergente',V:'Validada OK',X:'Validada divergente'})[c]||c||'Não iniciada';};
    self.statusAdmFilaLabel=function(c){return ({V:'Validada',X:'Aprovada com divergências',O:'Pendente de validação',D:'Pendente de validação'})[c]||'—';};
    self.filaFiltrada=function(){return self.filaOrdens.filter(function(l){
      var f=self.filtroStatusFila, s=l.status||'N';
      var ok=f==='TODAS'||f===s||(f==='FINALIZADAS'&&['O','D'].indexOf(s)>=0)||(f==='VALIDADAS'&&['V','X'].indexOf(s)>=0);
      return ok&&(!self.filtroOcFila||String(l.ordem)===String(self.filtroOcFila).trim())&&((self.filtroDocaFila===null||self.filtroDocaFila===undefined||self.filtroDocaFila==='')||String(l.doca||'')===String(self.filtroDocaFila));
    });};
    self.aplicarFiltrosFila=function(){
      if(self.filaCarregando)return;
      if(!self.empresa && String(self.nomeEmpresa||'').trim()){self.filaMensagem='Selecione uma empresa da lista antes de aplicar o filtro.';return;}
      self.focoNomeEmpresa=false;self.sugestoesEmpresa=[];
      self.filaCarregando=true;self.filaMensagem='Carregando ordens de carga...';
      self.requisicaoBusca({CODEMP:Number(self.empresa)||0,ORDEMCARGA:Number(self.filtroOcFila)||0,MODO:'FILA',STATUSFILA:self.filtroStatusFila,DOCA:(self.filtroDocaFila===null||self.filtroDocaFila===undefined||self.filtroDocaFila==='')?'':self.filtroDocaFila}).then(function(r){
        $scope.$applyAsync(function(){self.filaOrdens=r.ordens||[];self.filaMensagem=self.filaOrdens.length+' ordens localizadas'+(r.limiteAtingido?' (limite de 500; refine os filtros).':'.');});
      }).catch(function(e){$scope.$applyAsync(function(){self.filaMensagem='Erro: '+e.message;});})
      .finally(function(){$scope.$applyAsync(function(){self.filaCarregando=false;});});
    };
    // v1.0.57: selecao objetiva exclusiva do coletor, mantendo a fila desktop.
    self.mensagemSelecaoColetor='';
    self.onSelecaoOcKeydown=function(evento){
      if(evento && (evento.key==='Enter'||evento.keyCode===13)){
        evento.preventDefault();self.buscarOcColetor();
      }
    };
    self.buscarOcColetor=function(){
      if(self.filaCarregando)return;
      var empresa=Number(self.empresa), oc=Number(self.filtroOcFila);
      if(!Number.isInteger(empresa)||empresa<=0){self.mensagemSelecaoColetor='Informe uma empresa válida.';return;}
      if(!Number.isInteger(oc)||oc<=0){self.mensagemSelecaoColetor='Informe uma Ordem de Carga válida.';return;}
      self.mensagemSelecaoColetor='';self.filaCarregando=true;
      self.requisicaoBusca({CODEMP:empresa,ORDEMCARGA:oc,MODO:'FILA',STATUSFILA:'TODAS',DOCA:''})
        .then(function(r){$scope.$applyAsync(function(){
          var ordem=(r.ordens||[]).filter(function(l){return Number(l.empresa)===empresa&&Number(l.ordem)===oc;})[0];
          if(!ordem){self.mensagemSelecaoColetor='Ordem de Carga não encontrada para a empresa informada.';return;}
          // v1.0.79: nao permitir reabrir no coletor conferencias finalizadas/validadas.
          // A fila devolve o status da ultima conferencia da OC, inclusive as historicas.
          var statusOc=String(ordem.status||'N').trim().toUpperCase();
          var avisosStatus={
            O:{titulo:'Conferência já finalizada!',mensagem:'Esta Ordem de Carga já foi conferida e finalizada. Aguarde a validação do Supervisor antes de realizar outra conferência.'},
            D:{titulo:'Conferência finalizada com divergência!',mensagem:'Esta Ordem de Carga já foi conferida com divergência e aguarda decisão do Supervisor. Somente uma reconferência autorizada permite nova leitura.'},
            V:{titulo:'Ordem de Carga já conferida!',mensagem:'Esta Ordem de Carga já foi finalizada e validada pelo Supervisor. Não é necessário realizar uma nova conferência.'},
            X:{titulo:'Ordem de Carga já conferida!',mensagem:'Esta Ordem de Carga já foi finalizada e validada com divergência pelo Supervisor. Não é necessário realizar uma nova conferência.'}
          };
          if(avisosStatus[statusOc]){
            self.abrirDialogo({titulo:avisosStatus[statusOc].titulo,mensagem:avisosStatus[statusOc].mensagem,tipo:'perigo',rotulo:'OK — Entendi',somenteAviso:true});
            return;
          }
          if(ordem.doca===null||ordem.doca===undefined||String(ordem.doca).trim()===''){
            self.mensagemSelecaoColetor='A Ordem de Carga não possui doca cadastrada.';return;
          }
          self.abrirOcFila(ordem);
        });})
        .catch(function(e){$scope.$applyAsync(function(){self.mensagemSelecaoColetor='Não foi possível consultar a OC: '+(e.message||e);});})
        .finally(function(){$scope.$applyAsync(function(){self.filaCarregando=false;});});
    };
    self.abrirOcFila=function(l){
      self.empresa=Number(l.empresa);self.nomeEmpresa=l.nomeEmpresa||'';
      self.ordemCarga=Number(l.ordem);self.doca=l.doca||null;self.aba='conferencia';
      self.telaAtual='detalhe';self.buscarConferencia();self.agendarFocoDoca();
    };
    self.voltarFila=function(){self.telaAtual='fila';if(!self.ehLayoutColetor())self.aplicarFiltrosFila();else self.mensagemSelecaoColetor='';};
    self.limparFiltrosFila=function(){self.empresa=null;self.nomeEmpresa='';self.sugestoesEmpresa=[];self.focoNomeEmpresa=false;self.buscaNomeSeq++;self.buscaCodigoSeq++;self.filtroOcFila='';self.filtroDocaFila=null;self.filtroStatusFila='TODAS';self.filaOrdens=[];self.filaMensagem='Informe os filtros e clique em Aplicar.';};
    self.requisicaoBusca = function (payload) {
      var url = new URL('service.sbr', window.location.href);
      url.searchParams.set('serviceName', 'ConferenciaCargaSP.buscarConferencia');
      url.searchParams.set('outputType', 'json');
      // ServiceContext exige parametros presentes na requisicao, mesmo opcionais.
      payload = Object.assign({CODEMP:0, ORDEMCARGA:0, FILTRO:'', MODO:'CONSULTA', STATUSFILA:'TODAS',DOCA:''}, payload);
      Object.keys(payload).forEach(function(k){url.searchParams.set(k, String(payload[k]));});
      var session = new URLSearchParams(window.location.search).get('mgeSession');
      if (session) url.searchParams.set('mgeSession', session);
      return fetch(url.toString(), {method:'POST',credentials:'same-origin',
        headers:{'Accept':'application/json','Content-Type':'application/json'},
        body:JSON.stringify({serviceName:'ConferenciaCargaSP.buscarConferencia',requestBody:payload})
      }).then(function(resp){return resp.text().then(function(body){
        if (!resp.ok) throw new Error('HTTP '+resp.status+': '+body.substring(0,200));
        return JSON.parse(body);
      });}).then(function(data){
        if (String(data.status)==='0' || data.status==='ERROR') throw new Error(data.statusMessage || 'Falha na busca.');
        var result=data.responseBody || {};
        if(typeof result==='string') result=JSON.parse(result);
        if(result.responseBody) result=result.responseBody;
        return result;
      });
    };
    self.empresaAlterada = function () {
      self.nomeEmpresa='';self.sugestoesEmpresa=[];self.focoNomeEmpresa=false;
      self.numeroConferencia=null;self.statusConferencia='';
      self.painelConferente=null;self.resultadoOC=null;
    };
    self.empresaCodigoDigitado = function () {
      self.buscaNomeSeq++;
      self.empresaAlterada();
      self.buscarEmpresa();
    };
    self.buscarEmpresa = function () {
      var codigo=Number(self.empresa),seq=++self.buscaCodigoSeq;
      if(!Number.isSafeInteger(codigo)||codigo<=0){self.nomeEmpresa='';return;}
      self.requisicaoBusca({CODEMP:codigo}).then(function(result){
        $scope.$applyAsync(function(){if(seq===self.buscaCodigoSeq && Number(self.empresa)===codigo)self.nomeEmpresa=result.nomeEmpresa||'';});
      }).catch(function(err){$scope.$applyAsync(function(){if(seq===self.buscaCodigoSeq){self.nomeEmpresa='';self.filaMensagem='Empresa: '+err.message;}});});
    };
    self.empresaNomeDigitado = function () {
      var texto=String(self.nomeEmpresa||'').trim(),seq=++self.buscaNomeSeq;
      self.buscaCodigoSeq++;self.empresa=null;self.sugestoesEmpresa=[];
      self.focoNomeEmpresa=true;
      if(!texto)return;
      self.requisicaoBusca({MODO:'PESQUISA_EMPRESA',FILTRO:texto}).then(function(result){
        $scope.$applyAsync(function(){if(seq===self.buscaNomeSeq && String(self.nomeEmpresa||'').trim()===texto)self.sugestoesEmpresa=result.empresas||[];});
      }).catch(function(err){$scope.$applyAsync(function(){if(seq===self.buscaNomeSeq)self.filaMensagem='Empresa: '+err.message;});});
    };
    self.abrirPesquisaEmpresa = function () {
      self.pesquisaEmpresaAberta=true;
      self.pesquisarEmpresas();
    };
    self.pesquisarEmpresas = function () {
      self.requisicaoBusca({MODO:'PESQUISA_EMPRESA',FILTRO:self.filtroEmpresa||''}).then(function(result){
        $scope.$applyAsync(function(){self.empresasEncontradas=result.empresas||[];self.mensagemEmpresas='';});
      }).catch(function(err){$scope.$applyAsync(function(){self.mensagemEmpresas=err.message;self.empresasEncontradas=[];});});
    };
    self.selecionarEmpresa = function (e) {
      self.buscaNomeSeq++;self.buscaCodigoSeq++;
      self.empresa=Number(e.codigo);self.empresaAlterada();self.nomeEmpresa=e.nome;
      self.sugestoesEmpresa=[];self.focoNomeEmpresa=false;self.pesquisaEmpresaAberta=false;
    };
    self.buscarConferencia = function () {
      var empresa=Number(self.empresa),ordem=Number(self.ordemCarga);
      if(!Number.isSafeInteger(empresa)||empresa<=0||!Number.isSafeInteger(ordem)||ordem<=0){
        self.mensagemBusca='Informe empresa e ordem de carga validas.';return;
      }
      if(self.buscandoConferencia) return;
      self.buscandoConferencia=true;
      self.reiniciarValidacaoDoca();
      self.numeroConferencia=null;self.painelConferente=null;self.statusConferencia='';self.statusCodigo='';self.mensagemFinalizacao='';self.ocLocalizadaParaInicio=false;self.conciliacao=null;self.resultadoOC=null;self.resultadoFIFO=null;self.resultadoCongelamento=null;
      self.mensagemBusca='Buscando ordem de carga e conferencia...';
      self.requisicaoBusca({CODEMP:empresa,ORDEMCARGA:ordem}).then(function(result){
        $scope.$applyAsync(function(){
          if(Number(self.empresa)!==empresa||Number(self.ordemCarga)!==ordem) return;
          self.nomeEmpresa=result.nomeEmpresa||'';
          self.doca=result.doca||null;
          self.ocLocalizadaParaInicio=!result.numeroConferencia;
          if(result.numeroConferencia){
            self.numeroConferencia=Number(result.numeroConferencia);
            self.statusCodigo=result.status;
            self.statusConferencia=({A:'EM ANDAMENTO',P:'PENDENTE',O:'FINALIZADA OK',D:'FINALIZADA DIVERGENTE',C:'CONCLUIDA',X:'CANCELADA'})[result.status]||result.status;
            self.mensagemBusca='Conferencia nº '+self.numeroConferencia+' localizada.';
            if(['A','O','D'].indexOf(result.status)>=0) self.consultarPainel();
            else self.mensagemBusca+=' Status '+result.status+'. Painel de leituras disponivel somente quando liberada.';
          }else{
            self.mensagemBusca='Nenhuma conferencia cadastrada. Use Iniciar conferencia para validar FIFO e congelar a previsao.';
          }
        });
      }).catch(function(err){$scope.$applyAsync(function(){self.mensagemBusca='Erro: '+err.message;});
      }).finally(function(){$scope.$applyAsync(function(){self.buscandoConferencia=false;});});
    };
    self.aba = "conferencia";
    self.consultarPainelSeNecessario = function () { if (!self.painelConferente && !self.consultandoPainel && Number(self.numeroConferencia) > 0) self.consultarPainel(); };
    self.empresa = null;
    self.ordemCarga = null;
    self.mensagem = '';
    self.resultadoServico = '';
    self.testandoServico = false;

    self.init = function () {
      self.mensagem = 'Interface carregada. Informe empresa e OC para testar os campos.';
    };

    self.verificarTela = function () {
      if (!(Number(self.empresa) > 0) || !(Number(self.ordemCarga) > 0)) {
        self.mensagem = 'Informe uma empresa e uma ordem de carga válidas.';
        return;
      }
      self.mensagem = 'Campos preenchidos: empresa ' + self.empresa + ', OC ' + self.ordemCarga + '. Consulta à OC ainda não habilitada.';
    };

    self.consultandoOC = false;
    self.resultadoOC = null;
    self.buscarOC = function () {
      var empresa = Number(self.empresa);
      var ordem = Number(self.ordemCarga);
      if (!Number.isSafeInteger(empresa) || empresa <= 0 ||
          !Number.isSafeInteger(ordem) || ordem <= 0) {
        self.mensagem = 'Informe empresa e ordem de carga validas.';
        return;
      }
      if (self.consultandoOC) return;
      self.consultandoOC = true;
      self.resultadoOC = null;
      self.resultadoFIFO = null;
      self.resultadoCongelamento = null;
      self.mensagem = 'Consultando ordem de carga...';

      // IMPORTANTE: contexto relativo do proprio Add-on, validado na v1.0.2.2.
      var url = new URL('service.sbr', window.location.href);
      url.searchParams.set('serviceName', 'ConferenciaCargaSP.consultarOC');
      url.searchParams.set('outputType', 'json');
      url.searchParams.set('CODEMP', String(empresa));
      url.searchParams.set('ORDEMCARGA', String(ordem));
      var session = new URLSearchParams(window.location.search).get('mgeSession');
      if (session) url.searchParams.set('mgeSession', session);

      fetch(url.toString(), {
        method: 'POST',
        credentials: 'same-origin',
        headers: {'Accept': 'application/json', 'Content-Type': 'application/json'},
        body: JSON.stringify({
          serviceName: 'ConferenciaCargaSP.consultarOC',
          requestBody: {CODEMP: empresa, ORDEMCARGA: ordem}
        })
      }).then(function (response) {
        return response.text().then(function (text) {
          if (!response.ok) throw new Error('HTTP ' + response.status + ': ' + text.substring(0, 250));
          return JSON.parse(text);
        });
      }).then(function (data) {
        if (String(data.status) === '0' || data.status === 'ERROR') {
          throw new Error(data.statusMessage || 'Falha na consulta Java.');
        }
        var result = data.responseBody || {};
        if (typeof result === 'string') result = JSON.parse(result);
        if (result.responseBody) result = result.responseBody;
        if (result.empresa === undefined && result.encontrada === undefined) {
          throw new Error('Resposta inesperada do servico: ' + JSON.stringify(data).substring(0, 300));
        }
        $scope.$applyAsync(function () {
          self.resultadoOC = result;
          self.mensagem = result.mensagem || 'Consulta concluida.';
        });
      }).catch(function (error) {
        $scope.$applyAsync(function () { self.mensagem = 'Falha na consulta: ' + error.message; });
      }).finally(function () {
        $scope.$applyAsync(function () { self.consultandoOC = false; });
      });
    };

    // Teste isolado de comunicação: não consulta banco e não grava informações.
    self.testarServicoJava = function () {
      if (self.testandoServico) return;
      self.testandoServico = true;
      self.resultadoServico = 'Aguardando resposta do serviço Java...';

      var params = new URLSearchParams({
        serviceName: 'ConferenciaCargaSP.testarConexao',
        outputType: 'json'
      });

      // O provider e registrado no WAR deste Add-on, nao no /mge principal.
      // A tela e aberta no contexto da extensao (/$ctx/CONF_CARGAS.xhtml5).
      var url = new URL('service.sbr', window.location.href);
      url.search = params.toString();
      var session = new URLSearchParams(window.location.search).get('mgeSession');
      if (session) url.searchParams.set('mgeSession', session);

      fetch(url.toString(), {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
        body: JSON.stringify({
          serviceName: 'ConferenciaCargaSP.testarConexao',
          requestBody: {}
        })
      })
      .then(function (response) {
        return response.text().then(function (body) {
          if (!response.ok) throw new Error('HTTP ' + response.status + ': ' + body.substring(0, 400));
          return body;
        });
      })
      .then(function (body) {
        var message = body;
        try {
          var data = JSON.parse(body);
          if (data.status === '0' || data.status === 0 || data.status === 'ERROR') {
            message = 'Resposta do serviço (verificar): ' + body;
          } else {
            message = data.statusMessage || (data.responseBody && data.responseBody.statusMessage) || body;
          }
        } catch (ignore) { /* Resposta não JSON: manter texto original. */ }
        console.log('ConferenciaCargaSP.testarConexao:', body);
        $scope.$applyAsync(function () { self.resultadoServico = message; });
      })
      .catch(function (error) {
        console.error('Falha no teste do serviço Java:', error);
        $scope.$applyAsync(function () { self.resultadoServico = 'Erro ao consultar serviço Java: ' + error.message; });
      })
      .finally(function () {
        $scope.$applyAsync(function () { self.testandoServico = false; });
      });
    };
    // Consulta exclusivamente o resumo da alocacao FIFO. Nunca transfere lotes/itens ao cliente.
    self.consultandoFIFO = false;
    self.resultadoFIFO = null;
    self.consultarFIFO = function () {
      if(!self.ehSupervisor())return;
      var empresa = Number(self.empresa), ordem = Number(self.ordemCarga);
      if (!Number.isSafeInteger(empresa) || empresa <= 0 ||
          !Number.isSafeInteger(ordem) || ordem <= 0) {
        self.mensagem = 'Informe empresa e ordem de carga validas.';
        return;
      }
      if (!self.resultadoOC || !self.resultadoOC.encontrada ||
          Number(self.resultadoOC.empresa) !== empresa ||
          Number(self.resultadoOC.ordemCarga) !== ordem) {
        self.mensagem = 'Busque primeiro a ordem de carga informada.';
        return;
      }
      if (self.consultandoFIFO) return;
      self.consultandoFIFO = true;
      self.resultadoFIFO = null;
      self.resultadoCongelamento = null;
      self.mensagem = 'Validando a alocacao FIFO do romaneio...';
      var url = new URL('service.sbr', window.location.href);
      url.searchParams.set('serviceName', 'ConferenciaCargaSP.consultarComposicao');
      url.searchParams.set('outputType', 'json');
      url.searchParams.set('CODEMP', String(empresa));
      url.searchParams.set('ORDEMCARGA', String(ordem));
      var session = new URLSearchParams(window.location.search).get('mgeSession');
      if (session) url.searchParams.set('mgeSession', session);
      fetch(url.toString(), {
        method: 'POST', credentials: 'same-origin',
        headers: {'Accept': 'application/json', 'Content-Type': 'application/json'},
        body: JSON.stringify({
          serviceName: 'ConferenciaCargaSP.consultarComposicao',
          requestBody: {CODEMP: empresa, ORDEMCARGA: ordem}
        })
      }).then(function (response) {
        return response.text().then(function (body) {
          if (!response.ok) throw new Error('HTTP ' + response.status + ': ' + body.substring(0, 200));
          return JSON.parse(body);
        });
      }).then(function (data) {
        if (String(data.status) === '0' || data.status === 'ERROR')
          throw new Error(data.statusMessage || 'Falha na validacao FIFO.');
        var result = data.responseBody || {};
        if (typeof result === 'string') result = JSON.parse(result);
        if (result.responseBody) result = result.responseBody;
        if (result.alocacaoCompleta === undefined)
          throw new Error('Resposta inesperada: ' + JSON.stringify(data).substring(0, 250));
        $scope.$applyAsync(function () {
          self.resultadoFIFO = result;
          self.mensagem = result.mensagem || 'Validacao concluida.';
        });
      }).catch(function (error) {
        $scope.$applyAsync(function () { self.mensagem = 'Erro ao validar FIFO: ' + error.message; });
      }).finally(function () {
        $scope.$applyAsync(function () { self.consultandoFIFO = false; });
      });
    };

    self.congelando = false;
    self.resultadoCongelamento = null;
    self.iniciarConferencia = function () {
      if(!self.ehSupervisor())return;
      var empresa = Number(self.empresa), ordem = Number(self.ordemCarga);
      if (!self.resultadoOC || !self.resultadoOC.encontrada ||
          Number(self.resultadoOC.empresa) !== empresa ||
          Number(self.resultadoOC.ordemCarga) !== ordem ||
          !self.resultadoFIFO || !self.resultadoFIFO.alocacaoCompleta) {
        self.mensagem = 'Busque a OC e valide a composicao FIFO antes de congelar.';
        return;
      }
      if (self.congelando || self.resultadoCongelamento) return;
      self.abrirDialogo({titulo:'Iniciar conferência',mensagem:'Congelar a previsão FIFO atual desta OC e liberar a conferência para leitura?',rotulo:'Iniciar conferência'}).then(function(ok){
      if(!ok)return;
      self.congelando = true;
      self.mensagem = 'Registrando previsao da OC...';
      var url = new URL('service.sbr', window.location.href);
      url.searchParams.set('serviceName', 'ConferenciaCargaSP.iniciarConferencia');
      url.searchParams.set('outputType', 'json');
      url.searchParams.set('CODEMP', String(empresa));
      url.searchParams.set('ORDEMCARGA', String(ordem));
      var session = new URLSearchParams(window.location.search).get('mgeSession');
      if (session) url.searchParams.set('mgeSession', session);
      fetch(url.toString(), {
        method: 'POST', credentials: 'same-origin',
        headers: {'Accept': 'application/json', 'Content-Type': 'application/json'},
        body: JSON.stringify({
          serviceName: 'ConferenciaCargaSP.iniciarConferencia',
          requestBody: {CODEMP: empresa, ORDEMCARGA: ordem}
        })
      }).then(function (response) {
        return response.text().then(function (body) {
          if (!response.ok) throw new Error('HTTP ' + response.status + ': ' + body.substring(0, 250));
          return JSON.parse(body);
        });
      }).then(function (data) {
        if (String(data.status) === '0' || data.status === 'ERROR')
          throw new Error(data.statusMessage || 'Falha ao congelar previsao.');
        var result = data.responseBody || {};
        if (typeof result === 'string') result = JSON.parse(result);
        if (result.responseBody) result = result.responseBody;
        if (!result.numeroConferencia) throw new Error('Resposta inesperada do servico.');
        $scope.$applyAsync(function () {
          self.resultadoCongelamento = result;
          self.mensagem = result.mensagem || 'Previsao congelada.';
          self.mensagemBusca=self.mensagem;
          self.ocLocalizadaParaInicio=false;
          self.numeroConferencia=Number(result.numeroConferencia);
          self.statusConferencia='EM ANDAMENTO';self.statusCodigo='A';
          self.consultarPainel();
        });
      }).catch(function (error) {
        $scope.$applyAsync(function () { self.mensagem = 'Falha ao iniciar: ' + error.message; });
      }).finally(function () {
        $scope.$applyAsync(function () { self.congelando = false; });
      });
      });
    };


    // A conferencia fisica exige STATUS=A, definido apos congelamento completo.
    // O servidor jamais envia a previsao para esta tela.
    self.numeroConferencia = null;
    $scope.$watch(function(){ return self.numeroConferencia; }, function(n, old){
      if (n !== old) { self.painelConferente = null; self.mensagemPainel = ''; }
    });
    // Navegacao de teclado do Zebra: Enter do scanner ou teclado fisico.
    // Ativada somente no layout estreito; o desktop mantem o fluxo anterior.
    self.ehLayoutColetor = function () {
      return !!(window.matchMedia && window.matchMedia('(max-width: 700px)').matches);
    };
    self.avancarLeitura = function (evento, campo) {
      if (!self.ehLayoutColetor() || !(evento.key === 'Enter' || evento.keyCode === 13)) return;
      evento.preventDefault();
      if (evento.repeat || self.registrandoLeitura) return;
      var valor = campo === 'barra' ? self.barraLeitura : (campo === 'lote' ? self.loteLeitura : self.quantidadeLeitura);
      if (valor === null || valor === undefined || String(valor).trim() === '') return;
      if (campo === 'quantidade') { self.registrarLeitura(); return; }
      var proximo = document.getElementById(campo === 'barra' ? 'conf-lote-fisico' : 'conf-quantidade');
      if (proximo) { proximo.focus(); if (campo === 'lote' && proximo.select) proximo.select(); }
    };
    // DataWedge pode enviar o codigo sem Enter. Aguarda o fim do fluxo de
    // caracteres para evitar mover o foco durante a leitura.
    var temporizadorBarra = null;
    var ultimaAlteracaoBarra = 0;
    var codigoAnteriorBarra = '';
    var apagandoCodigoBarra = false;
    var suspenderAvancoBarraAte = 0;
    self.onCodigoBarrasKeydown = function(evento){
      if(self.ehLayoutColetor() && (evento.key==='Backspace'||evento.key==='Delete'||evento.keyCode===8||evento.keyCode===46)){
        apagandoCodigoBarra=true;
        suspenderAvancoBarraAte=Date.now()+650;
        ++ultimaAlteracaoBarra;
        if(temporizadorBarra){clearTimeout(temporizadorBarra);temporizadorBarra=null;}
      }
      self.avancarLeitura(evento,'barra');
    };
    self.onCodigoBarrasKeyup = function(evento){
      if(evento.key==='Backspace'||evento.key==='Delete'||evento.keyCode===8||evento.keyCode===46){
        apagandoCodigoBarra=false;
        suspenderAvancoBarraAte=Date.now()+650;
      }
    };
    self.aoAlterarCodigoBarras = function () {
      if (temporizadorBarra) { clearTimeout(temporizadorBarra); temporizadorBarra = null; }
      var codigoAtual=String(self.barraLeitura||'');
      var reduziuCodigo=codigoAtual.length<codigoAnteriorBarra.length;
      codigoAnteriorBarra=codigoAtual;
      if(reduziuCodigo){
        ++ultimaAlteracaoBarra;
        suspenderAvancoBarraAte=Date.now()+650;
      }
      // Consulta da unidade em ambos os layouts; somente o avanco de foco e exclusivo do coletor.
      if (self.precisaValidarDoca() || self.registrandoLeitura || self.statusCodigo !== 'A') return;
      self.consultarUnidadePadrao();
      if (!self.ehLayoutColetor() || apagandoCodigoBarra || reduziuCodigo || Date.now()<suspenderAvancoBarraAte) return;
      var codigo = String(self.barraLeitura || '').trim();
      // Codigos muito curtos nao disparam mudanca automatica acidental.
      if (codigo.length < 4) return;
      ultimaAlteracaoBarra = Date.now();
      var alteracaoAtual = ultimaAlteracaoBarra;
      temporizadorBarra = setTimeout(function () {
        temporizadorBarra = null;
        if (alteracaoAtual !== ultimaAlteracaoBarra || !self.ehLayoutColetor() || apagandoCodigoBarra || Date.now()<suspenderAvancoBarraAte) return;
        var campo = document.getElementById('conf-codigo-barras');
        var lote = document.getElementById('conf-lote-fisico');
        if (campo && lote && document.activeElement === campo &&
            String(self.barraLeitura || '').trim() === codigo && self.statusCodigo === 'A') {
          lote.focus();
        }
      }, 220);
    };
    // Validacao operacional da doca por abertura de OC no layout do coletor.
    // Nao constitui controle de autorizacao contra chamadas diretas ao servico.
    // O campo da doca e criado por ng-if apos a consulta da OC.
    // Foco sem rolagem; tentativas posteriores cobrem a renderizacao do Angular.
    var focoDocaTimer = null;
    self.focarDoca = function () {
      if (!self.ehLayoutColetor() || !self.precisaValidarDoca() || self.telaAtual !== 'detalhe') return;
      var campo = document.getElementById('conf-doca-leitura');
      if (!campo || campo.disabled) return;
      try { campo.focus({preventScroll:true}); } catch (e) { campo.focus(); }
    };
    self.agendarFocoDoca = function () {
      if (focoDocaTimer) clearTimeout(focoDocaTimer);
      focoDocaTimer = setTimeout(function () {
        focoDocaTimer = null;
        self.focarDoca();
      }, 120);
    };
    self.docaLida = '';
    self.docaValidada = false;
    self.validandoDoca = false;
    self.mensagemDoca = '';
    var temporizadorDoca = null;
    self.reiniciarValidacaoDoca = function () {
      if (temporizadorDoca) { clearTimeout(temporizadorDoca); temporizadorDoca = null; }
      self.docaLida = ''; self.docaValidada = false; self.validandoDoca = false; self.mensagemDoca = '';
      self.agendarFocoDoca();
    };
    self.precisaValidarDoca = function () {
      return self.ehLayoutColetor() && !self.docaValidada;
    };
    self.aoAlterarDoca = function () {
      if (temporizadorDoca) clearTimeout(temporizadorDoca);
      if (!self.ehLayoutColetor() || self.validandoDoca || self.docaValidada) return;
      var codigo = String(self.docaLida || '').trim();
      if (!/^\d+$/.test(codigo)) return;
      temporizadorDoca = setTimeout(function () {
        temporizadorDoca = null;
        if (String(self.docaLida || '').trim() === codigo) self.validarDoca();
      }, 350);
    };
    self.onDocaKeydown = function (event) {
      if (event && (event.key === 'Enter' || event.keyCode === 13)) {
        event.preventDefault();
        self.validarDoca();
      }
    };
    self.validarDoca = function () {
      if (self.validandoDoca || self.docaValidada || !self.ehLayoutColetor()) return;
      var codigo = String(self.docaLida || '').trim();
      if (!/^\d+$/.test(codigo)) { self.mensagemDoca='Bipe o numero da doca.'; self.agendarFocoDoca(); return; }
      var empresa=Number(self.empresa), ordem=Number(self.ordemCarga);
      if (!Number.isSafeInteger(empresa) || !Number.isSafeInteger(ordem) || empresa<=0 || ordem<=0) return;
      var payload={CODEMP:empresa,ORDEMCARGA:ordem,DOCA_LIDA:codigo};
      var url=new URL('service.sbr',window.location.href);
      url.searchParams.set('serviceName','ConferenciaCargaSP.validarDoca');
      url.searchParams.set('outputType','json');
      Object.keys(payload).forEach(function(k){url.searchParams.set(k,String(payload[k]));});
      var session=new URLSearchParams(window.location.search).get('mgeSession');
      if(session) url.searchParams.set('mgeSession',session);
      self.validandoDoca=true;self.mensagemDoca='Validando doca...';
      fetch(url.toString(),{method:'POST',credentials:'same-origin',headers:{'Accept':'application/json','Content-Type':'application/json'},body:JSON.stringify({serviceName:'ConferenciaCargaSP.validarDoca',requestBody:payload})})
        .then(function(resp){return resp.text().then(function(body){if(!resp.ok)throw new Error('HTTP '+resp.status);return JSON.parse(body);});})
        .then(function(data){
          if(String(data.status)==='0'||data.status==='ERROR')throw new Error(data.statusMessage||'Falha ao validar doca.');
          var result=data.responseBody||{};if(typeof result==='string')result=JSON.parse(result);
          if(result.responseBody)result=result.responseBody;
          $scope.$applyAsync(function(){
            if(Number(self.empresa)!==empresa||Number(self.ordemCarga)!==ordem)return;
            self.docaValidada=result.validada===true;
            self.mensagemDoca=self.docaValidada?'Doca validada. Conferencia liberada.':(result.mensagem||'Doca incorreta.');
            if(self.docaValidada) setTimeout(function(){self.focarCodigoBarras();},80);
            else self.agendarFocoDoca();
          });
        }).catch(function(err){$scope.$applyAsync(function(){self.mensagemDoca='Erro: '+err.message;self.docaValidada=false;self.agendarFocoDoca();});})
        .finally(function(){$scope.$applyAsync(function(){self.validandoDoca=false;});});
    };
    $scope.$watch(function () {
      return self.telaAtual === 'detalhe' && self.ehLayoutColetor() && self.precisaValidarDoca();
    }, function (ativo, anterior) {
      if (ativo && !anterior) self.agendarFocoDoca();
    });
    self.barraLeitura = '';
    self.loteLeitura = '';
    self.unidadeLeitura = '';
    self.consultandoUnidade = false;
    self.mensagemUnidade = '';
    var ultimaConsultaUnidade = 0;
    var timerConsultaUnidade = null;
    self.consultarUnidadePadrao = function () {
      if (timerConsultaUnidade) clearTimeout(timerConsultaUnidade);
      var barra = String(self.barraLeitura || '').trim();
      var versao = ++ultimaConsultaUnidade;
      self.unidadeLeitura = '';
      self.mensagemUnidade = '';
      if (!barra) { self.consultandoUnidade = false; return; }
      timerConsultaUnidade = setTimeout(function () {
        self.consultandoUnidade = true;
        var url = new URL('service.sbr', window.location.href);
        url.searchParams.set('serviceName', 'ConferenciaCargaSP.consultarUnidadeProduto');
        url.searchParams.set('outputType', 'json');
        url.searchParams.set('CODBARRA', barra);
        var session = new URLSearchParams(window.location.search).get('mgeSession');
        if (session) url.searchParams.set('mgeSession', session);
        fetch(url.toString(), {method:'POST',credentials:'same-origin',
          headers:{'Accept':'application/json','Content-Type':'application/json'},
          body:JSON.stringify({serviceName:'ConferenciaCargaSP.consultarUnidadeProduto',requestBody:{CODBARRA:barra}})
        }).then(function(resp){return resp.text().then(function(body){
          if(!resp.ok) throw new Error('HTTP '+resp.status);
          return JSON.parse(body);
        });}).then(function(data){
          if(String(data.status)==='0'||data.status==='ERROR') throw new Error(data.statusMessage||'Falha ao consultar unidade.');
          var result=data.responseBody||{};
          if(typeof result==='string')result=JSON.parse(result);
          if(result.responseBody)result=result.responseBody;
          if(!result.codvol)throw new Error('Unidade padrao nao encontrada.');
          $scope.$applyAsync(function(){
            if(versao!==ultimaConsultaUnidade || String(self.barraLeitura||'').trim()!==barra)return;
            self.unidadeLeitura=String(result.codvol).trim().toUpperCase();
            self.mensagemUnidade='';
          });
        }).catch(function(err){$scope.$applyAsync(function(){
          if(versao===ultimaConsultaUnidade)self.mensagemUnidade=err.message+' Informe a unidade manualmente.';
        });}).finally(function(){$scope.$applyAsync(function(){
          if(versao===ultimaConsultaUnidade)self.consultandoUnidade=false;
        });});
      }, 260);
    };
    self.quantidadeLeitura = 1;
    self.registrandoLeitura = false;
    self.mensagemLeitura = '';
    self.totalLeiturasSessao = 0;
    self.eventoPendente = null;
    self.registrarLeitura = function (confirmarLote) {
      if (self.precisaValidarDoca()) { self.mensagemLeitura='Valide a doca antes de registrar leituras.'; return; }
      var n = Number(self.numeroConferencia), q = Number(self.quantidadeLeitura);
      var barra = String(self.barraLeitura || '').trim();
      var lote = String(self.loteLeitura || '').trim();
      var unidade = String(self.unidadeLeitura || '').trim().toUpperCase();
      if (!Number.isSafeInteger(n) || n <= 0 || !barra || !lote || !unidade ||
          !(q > 0) || !Number.isFinite(q)) {
        self.mensagemLeitura = 'Informe conferencia, codigo, lote, unidade e quantidade positiva.';
        return;
      }
      if (self.registrandoLeitura || self.consultandoUnidade) return;
      if (!self.eventoPendente) {
        self.eventoPendente = 'CONF-' + Date.now() + '-' + Math.random().toString(36).slice(2);
      }
      var payload = {NUCONF:n,CODBARRA:barra,CONTROLE:lote,CODVOL:unidade,QTD:q,IDEVENTO:self.eventoPendente,CONFIRMAR_LOTE:confirmarLote===true?'S':'N'};
      var url = new URL('service.sbr',window.location.href);
      url.searchParams.set('serviceName','ConferenciaCargaSP.registrarLeitura');
      url.searchParams.set('outputType','json');
      Object.keys(payload).forEach(function(k){url.searchParams.set(k,String(payload[k]));});
      var session = new URLSearchParams(window.location.search).get('mgeSession');
      if (session) url.searchParams.set('mgeSession',session);
      self.registrandoLeitura = true;
      self.mensagemLeitura = 'Registrando leitura...';
      fetch(url.toString(),{method:'POST',credentials:'same-origin',
        headers:{'Accept':'application/json','Content-Type':'application/json'},
        body:JSON.stringify({serviceName:'ConferenciaCargaSP.registrarLeitura',requestBody:payload})
      }).then(function(resp){return resp.text().then(function(body){
        if(!resp.ok) throw new Error('HTTP '+resp.status+': '+body.substring(0,200));
        return JSON.parse(body);
      });}).then(function(data){
        if(String(data.status)==='0'||data.status==='ERROR') throw new Error(data.statusMessage||'Falha no registro.');
        var result=data.responseBody||{};
        if(typeof result==='string') result=JSON.parse(result);
        if(result.responseBody) result=result.responseBody;
        if(result.avisoLote){
          self.registrandoLeitura=false;
          return self.abrirDialogo({titulo:'Lote divergente!',mensagem:'O lote informado nao corresponde ao previsto para este produto. Deseja registrar mesmo assim? O Supervisor analisara a divergencia.',tipo:'perigo',rotulo:'Registrar mesmo assim',rotuloCancelar:'Corrigir lote'}).then(function(ok){
            if(ok) self.registrarLeitura(true);
            else $scope.$applyAsync(function(){self.mensagemLeitura='Registro cancelado. Corrija o lote e tente novamente.';});
          });
        }
        if(!result.numeroLeitura) throw new Error('Resposta inesperada: '+JSON.stringify(data).substring(0,250));
        $scope.$applyAsync(function(){
          self.mensagemLeitura=result.mensagem+' Nº '+result.numeroLeitura;
          if(!result.repetida) self.totalLeiturasSessao++;
          self.barraLeitura='';self.loteLeitura='';self.quantidadeLeitura=1;
          ++ultimaConsultaUnidade; self.unidadeLeitura=''; self.mensagemUnidade='';
          self.eventoPendente=null;
          self.consultarPainel();
          self.focarCodigoBarras();
        });
      }).catch(function(err){$scope.$applyAsync(function(){self.mensagemLeitura='Erro: '+err.message+' (reenvio conserva identificador)';});
        if(self.ehLayoutColetor()){
          var mensagem=String(err && err.message || 'Falha ao registrar a leitura.');
          var produtoFora=/n[aã]o previsto|fora da (oc|ordem)|n[aã]o pertence|produto.*(ordem|carga)/i.test(mensagem);
          var barraInvalida=/c[oó]digo de barras|codbarr|refer[eê]ncia|produto n[aã]o encontrad|barra inv[aá]lid/i.test(mensagem);
          self.abrirDialogo({titulo:produtoFora?'Produto não previsto na OC!':(barraInvalida?'Código de barras inválido!':'Erro ao registrar leitura'),mensagem:mensagem+'\n\nLeitura não registrada.',tipo:'perigo',rotulo:'OK',somenteAviso:true}).then(function(){self.focarCodigoBarras();});
        }
      }).finally(function(){$scope.$applyAsync(function(){self.registrandoLeitura=false;});});
    };

    self.painelConferente = null;
    self.consultandoPainel = false;
    self.mensagemPainel = '';
    self.consultarPainel = function () {
      var n = Number(self.numeroConferencia);
      if (!Number.isSafeInteger(n) || n <= 0) {
        self.mensagemPainel = 'Informe um numero de conferencia valido.';
        self.painelConferente = null;
        return;
      }
      if (self.consultandoPainel) return;
      self.consultandoPainel = true;
      self.mensagemPainel = 'Consultando produtos e leituras...';
      self.painelConferente = null;
      var url = new URL('service.sbr', window.location.href);
      url.searchParams.set('serviceName', 'ConferenciaCargaSP.consultarPainelConferente');
      url.searchParams.set('outputType', 'json');
      url.searchParams.set('NUCONF', String(n));
      var session = new URLSearchParams(window.location.search).get('mgeSession');
      if (session) url.searchParams.set('mgeSession', session);
      fetch(url.toString(), {method:'POST', credentials:'same-origin',
        headers:{'Accept':'application/json','Content-Type':'application/json'},
        body:JSON.stringify({serviceName:'ConferenciaCargaSP.consultarPainelConferente',requestBody:{NUCONF:n}})
      }).then(function(resp){return resp.text().then(function(body){
        if (!resp.ok) throw new Error('HTTP '+resp.status+': '+body.substring(0,200));
        return JSON.parse(body);
      });}).then(function(data){
        if (String(data.status)==='0' || data.status==='ERROR')
          throw new Error(data.statusMessage || 'Falha na consulta.');
        var result=data.responseBody||{};
        if (typeof result==='string') result=JSON.parse(result);
        if (result.responseBody) result=result.responseBody;
        if (!Array.isArray(result.produtos) || !Array.isArray(result.leituras))
          throw new Error('Resposta inesperada do servico.');
        $scope.$applyAsync(function(){
          if (Number(self.numeroConferencia)!==n) return;
          self.linhaSelecionada=null;
          self.painelConferente=result;
          self.mensagemPainel='Consulta atualizada.';
        });
      }).catch(function(err){$scope.$applyAsync(function(){self.mensagemPainel='Erro: '+err.message;});
      }).finally(function(){$scope.$applyAsync(function(){self.consultandoPainel=false;});});
    };

    $scope.$evalAsync(function(){self.carregarPerfilConferencia();});
  }]);
